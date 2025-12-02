package com.parallel.wordcount;

import com.parallel.wordcount.ui.WordCountApp;

/**
 * Inicia a interface grafica (padrao) ou o modo CLI quando houver argumentos.
 *
 * - Sem argumentos ou com "--gui": abre a interface Swing.
 * - Com outros argumentos: repassa para o BenchmarkRunner (CLI).
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || (args.length > 0 && "--gui".equalsIgnoreCase(args[0]))) {
            WordCountApp.main(new String[0]);
        } else {
            BenchmarkRunner.main(args);
        }
    }
}
