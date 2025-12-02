package com.parallel.wordcount;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_DEVICE_TYPE_ACCELERATOR;
import static org.jocl.CL.CL_DEVICE_TYPE_CPU;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_PROGRAM_BUILD_LOG;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueue;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clSetKernelArg;
import static org.jocl.CL.clGetProgramBuildInfo;
import static org.jocl.CL.clReleaseMemObject;

/**
 * Parallel counting using OpenCL through JOCL. Falls back to CPU OpenCL device when GPU is not present.
 */
public class ParallelGpuCounter implements WordCounter {

    /**
     * Cache de recursos OpenCL por device para evitar rebuild do programa a cada execucao.
     */
    private static final Map<String, CachedResources> CACHE = new ConcurrentHashMap<>();

    private static final String KERNEL_SOURCE = """
            __kernel void countWord(__global const uchar* text,
                                    const int textLen,
                                    __global const uchar* word,
                                    const int wordLen,
                                    __global int* counter) {
                int gid = get_global_id(0);
                if (gid + wordLen > textLen) {
                    return;
                }
                int match = 1;
                for (int i = 0; i < wordLen; i++) {
                    if (text[gid + i] != word[i]) {
                        match = 0;
                        break;
                    }
                }
                if (match) {
                    atomic_add(counter, 1);
                }
            }
            """;

    @Override
    public String name() {
        return "ParallelGPU";
    }

    @Override
    public WordCountResult count(String datasetName, String text, String targetWord) {
        if (targetWord.isBlank()) {
            throw new IllegalArgumentException("Target word must not be blank");
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String normalizedTarget = targetWord.toLowerCase(Locale.ROOT);
        byte[] textBytes = normalizedText.getBytes(StandardCharsets.UTF_8);
        byte[] wordBytes = normalizedTarget.getBytes(StandardCharsets.UTF_8);

        if (wordBytes.length == 0 || textBytes.length == 0) {
            return new WordCountResult(name(), datasetName, 0, 0, null, "GPU");
        }

        CL.setExceptionsEnabled(true);

        OpenClDevice device = selectDevice();
        long start = System.nanoTime();
        int occurrences = runKernel(device, textBytes, wordBytes);
        long elapsed = System.nanoTime() - start;
        String deviceLabel = device.typeLabel() + " (" + device.name() + ")";
        return new WordCountResult(name(), datasetName, occurrences, elapsed / 1_000_000, null, deviceLabel);
    }

    private int runKernel(OpenClDevice device, byte[] textBytes, byte[] wordBytes) {
        CachedResources resources = CACHE.computeIfAbsent(device.name(), k -> buildResources(device));
        cl_context context = resources.context();
        cl_command_queue queue = resources.queue();
        cl_kernel kernel = resources.kernel();

        cl_mem textMem = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_char * textBytes.length, Pointer.to(textBytes), null);
        cl_mem wordMem = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_char * wordBytes.length, Pointer.to(wordBytes), null);
        int[] zero = new int[]{0};
        cl_mem countMem = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(zero), null);

        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(textMem));
        clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[]{textBytes.length}));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(wordMem));
        clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{wordBytes.length}));
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(countMem));

        long[] globalWorkSize = new long[]{textBytes.length};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        clFinish(queue);

        int[] result = new int[1];
        clEnqueueReadBuffer(queue, countMem, CL.CL_TRUE, 0, Sizeof.cl_int, Pointer.to(result), 0, null, null);

        // Cleanup
        clReleaseMemObject(textMem);
        clReleaseMemObject(wordMem);
        clReleaseMemObject(countMem);

        return result[0];
    }

    private CachedResources buildResources(OpenClDevice device) {
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, device.platform());
        cl_context context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device.id()}, null, null, null);

        cl_command_queue queue = clCreateCommandQueue(context, device.id(), 0, null);

        cl_program program = clCreateProgramWithSource(context, 1, new String[]{KERNEL_SOURCE}, null, null);
        int buildResult = clBuildProgram(program, 0, null, null, null, null);
        if (buildResult != CL.CL_SUCCESS) {
            throw new IllegalStateException("OpenCL program build failed: " + buildLog(program, device.id()));
        }
        cl_kernel kernel = clCreateKernel(program, "countWord", null);
        return new CachedResources(context, queue, program, kernel);
    }

    private String buildLog(cl_program program, cl_device_id device) {
        long[] logSize = new long[1];
        clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, logSize);
        byte[] logData = new byte[(int) logSize[0]];
        clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, logSize[0], Pointer.to(logData), null);
        return new String(logData, StandardCharsets.UTF_8);
    }

    private OpenClDevice selectDevice() {
        int[] numPlatformsArr = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArr);
        if (numPlatformsArr[0] == 0) {
            throw new IllegalStateException("No OpenCL platforms found. Install GPU/CPU OpenCL drivers.");
        }
        cl_platform_id[] platforms = new cl_platform_id[numPlatformsArr[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        List<OpenClDevice> devices = new ArrayList<>();
        for (cl_platform_id platform : platforms) {
            collectDevices(platform, CL_DEVICE_TYPE_GPU, "GPU", devices);
        }
        if (devices.isEmpty()) {
            for (cl_platform_id platform : platforms) {
                collectDevices(platform, CL_DEVICE_TYPE_ACCELERATOR, "Accelerator", devices);
            }
        }
        if (devices.isEmpty()) {
            for (cl_platform_id platform : platforms) {
                collectDevices(platform, CL_DEVICE_TYPE_CPU, "CPU", devices);
            }
        }
        if (devices.isEmpty()) {
            throw new IllegalStateException("No suitable OpenCL devices available.");
        }
        return devices.get(0);
    }

    private void collectDevices(cl_platform_id platform, long type, String label, List<OpenClDevice> devices) {
        int[] numDevicesArr = new int[1];
        int res = clGetDeviceIDs(platform, type, 0, null, numDevicesArr);
        if (res != CL.CL_SUCCESS || numDevicesArr[0] == 0) {
            return;
        }
        cl_device_id[] ids = new cl_device_id[numDevicesArr[0]];
        clGetDeviceIDs(platform, type, ids.length, ids, null);
        for (cl_device_id id : ids) {
            devices.add(new OpenClDevice(platform, id, label, deviceName(id)));
        }
    }

    private String deviceName(cl_device_id deviceId) {
        long[] size = new long[1];
        clGetDeviceInfo(deviceId, CL_DEVICE_NAME, 0, null, size);
        byte[] data = new byte[(int) size[0]];
        clGetDeviceInfo(deviceId, CL_DEVICE_NAME, size[0], Pointer.to(data), null);
        return new String(data, StandardCharsets.UTF_8).trim();
    }

    private record OpenClDevice(cl_platform_id platform, cl_device_id id, String typeLabel, String name) {
    }

    private record CachedResources(cl_context context, cl_command_queue queue, cl_program program, cl_kernel kernel) {
    }
}
