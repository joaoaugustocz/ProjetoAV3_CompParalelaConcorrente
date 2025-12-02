# Analise comparativa de algoritmos com uso de paralelismo

## Resumo
Aplicacao em Java para contar ocorrencias de uma palavra em textos usando tres abordagens: serial na CPU, paralela na CPU e paralela na GPU via OpenCL (JOCL). O framework executa baterias de testes, gera CSV com os tempos e um grafico com as medias por dataset.

## Introducao
Foram escolhidos tres metodos: um loop simples (SerialCPU), um particionamento em threads (ParallelCPU) e um kernel OpenCL executado em GPU ou dispositivo OpenCL disponivel (ParallelGPU). A comparacao avalia como o paralelismo se comporta em textos de tamanhos diferentes e com configuracoes de threads distintas.

## Metodologia
- Dados: tres amostras em `data/` (small, medium, large) com repeticoes da palavra "paralelismo".
- Medicao: `System.nanoTime` para cada execucao. Resultados registrados no CSV `results/wordcount_*.csv`.
- Configuracoes: `--runs` define quantas repeticoes (padrao 3); `--threads` aceita lista para variar nucleos na CPU; GPU e executada se existir driver OpenCL (fallback para CPU OpenCL).
- Saida visual: grafico PNG gerado a partir das medias por dataset/metodo.

## Execucao (CLI)
```bash
mvn clean package -DskipTests
java -jar target/wordcount-parallel-1.0.0-jar-with-dependencies.jar ^
  --word paralelismo ^
  --inputs data/sample_small.txt,data/sample_medium.txt,data/sample_large.txt ^
  --threads 2,4 ^
  --runs 3
```
Opcoes relevantes:
- `--csv <arquivo>`: define onde salvar o CSV (padrao `results/wordcount_TIMESTAMP.csv`).
- `--chart <arquivo>`: define onde salvar o grafico (padrao `results/wordcount_chart.png`).
- `--skip-gpu`: desativa a execucao OpenCL caso nao haja driver.

Atalhos: `run.bat` executa o CLI com os padroes do projeto.

## Interface grafica (dashboard estilo AV2)
- Gere o JAR: `mvn clean package -DskipTests`
- Abra `run-gui.bat` (duplo clique) ou rode `java -jar target/wordcount-parallel-1.0.0-jar-with-dependencies.jar --gui`
- Na tela: escolha palavra alvo, datasets (carregue textos), quantidade de amostras, lista de threads para ParallelCPU e habilite/desabilite Serial/CPU/GPU. Clique em **Executar** para ver:
  - Grafico animado de tempo medio por dataset/metodo (similar ao AV2).
  - Tabela com as ultimas execucoes (dataset, metodo, threads/device, tempo e ocorrencias).
  - Botao **Exportar CSV + Grafico** gera `results/wordcount_ui_*.csv` e `results/wordcount_ui_*.png` com o historico da sessao.
  - Botao **Limpar** reseta grafico, tabela e historico.

## Resultados e Discussao
Ambiente usado no exemplo: Windows 11, CPU com 8 nucleos logicos, GPU NVIDIA GeForce RTX 3070 Ti Laptop (OpenCL). Comando executado: `--word paralelismo --threads 2,4 --runs 3`.

Medias por dataset (ms):

| Dataset            | SerialCPU (1t) | ParallelCPU (2t) | ParallelCPU (4t) | ParallelGPU | Ocorrencias |
|--------------------|----------------|------------------|------------------|-------------|-------------|
| sample_small.txt   | ~0.00          | 2.67             | 1.33             | 427.67      | 9           |
| sample_medium.txt  | ~0.00          | 0.33             | 0.67             | 123.33      | 17          |
| sample_large.txt   | ~0.00          | 0.33             | 1.33             | 103.67      | 27          |

Arquivos gerados no exemplo:
- CSV: `results/wordcount_20251201_203928.csv`
- Grafico: `results/wordcount_chart.png`

Observacoes:
- Os textos de exemplo sao pequenos; o overhead de inicializacao domina e faz o SerialCPU aparecer com 0 ms (resolucao do relogio). Use textos maiores para diferencas mais claras.
- ParallelCPU ganha pouco em textos curtos; com datasets maiores ou mais threads a diferenca tende a crescer.
- ParallelGPU teve latencias maiores devido a transferencia de memoria e setup do kernel. Em workloads massivos a GPU tende a superar a CPU.
- Ajustar `--threads` permite avaliar o impacto do numero de nucleos e observar quando o overhead de sincronizacao supera o ganho.

### Notas sobre GPU, overhead e variantes

- Overhead de transferencia domina em workloads leves: contar uma substring e compute-levissima e limitada por banda de memoria. Para textos de poucas dezenas de MB, o tempo de copiar o buffer para a GPU e sincronizar o kernel pode superar o tempo de um scan na CPU (inclusive com threads).
- Atomics vs. reducao local: a variante `ParallelGPU` (basica) faz `atomic_add` global em cada match, criando contencao quando ha muitas ocorrencias. As variantes `ParallelGPU Optimized` e `ParallelGPU Optimized (cached text)` usam reducao em local memory, fazendo apenas um `atomic_add` por bloco para minimizar o custo.
- Texto em cache na GPU: `ParallelGPU Optimized (cached text)` mantem o buffer de texto residente na GPU e mede principalmente o tempo de kernel + leitura do contador, ignorando o upload do texto. Isso mostra o potencial computacional da GPU, mas nao inclui o custo de I/O de carregar o buffer a cada execucao.
- Por que a CPU ainda ganha em muitos cenarios:
  - A operacao e simples (comparar alguns bytes) e memoria-bound; CPUs com 4-16 threads saturam a RAM rapidamente.
  - O custo de copiar 300-800 MB via PCIe + latencia de lancar kernel e alto em relacao ao trabalho por byte.
  - Mesmo otimizando atomics, o limite passa a ser banda de memoria global; a GPU nao fica "no limite" de FLOPs, e sim de memoria.
- Quando a GPU tende a vencer:
  - Workloads muito maiores (centenas de MB a GB) e/ou multiplas execucoes sobre o mesmo buffer (reuso do texto na GPU).
  - Operacoes mais pesadas por byte (ex.: hashing, normalizacao complexa, pipelines com mais computacao), nas quais o overhead de copia se dilui.
  - Overlap de copia e execucao (pinned memory + filas assincronas) e kernels que processam mais bytes por thread para elevar o trabalho util.

## Conclusao
O exercicio mostra que paralelismo so compensa quando o volume de dados justifica o custo adicional. Em textos curtos, a versao serial e a paralela em CPU apresentam tempos semelhantes, enquanto a GPU sofre com overhead inicial. Para textos maiores ou pipelines que processam muitos arquivos, a GPU e mais threads de CPU tendem a reduzir o tempo total.

## Referencias
- OpenCL Specification: https://www.khronos.org/opencl/
- JOCL 2.0.4: http://jocl.org/
- XChart: https://knowm.org/open-source/xchart/

## Anexos - Codigos das implementacoes
- Contadores: `src/main/java/com/parallel/wordcount/SerialCpuCounter.java`, `ParallelCpuCounter.java`, `ParallelGpuCounter.java`, `ParallelGpuOptimizedCounter.java`, `ParallelGpuOptimizedCachedCounter.java`
- CLI/benchmark: `src/main/java/com/parallel/wordcount/BenchmarkRunner.java`
- Interface grafica: `src/main/java/com/parallel/wordcount/ui/*`, script `run-gui.bat`
- Utilitarios: `TextLoader.java`, `CsvExporter.java`, `ChartGenerator.java`
- Amostras: `data/sample_small.txt`, `data/sample_medium.txt`, `data/sample_large.txt` (arquivos maiores devem ser gerados localmente: `sample_huge.txt`, `sample_mega.txt`)
- Link do repositorio GitHub: https://github.com/joaoaugustocz/ProjetoAV3_CompParalelaConcorrente
