package com.elias.eliasibmtest.controller;

import com.elias.eliasibmtest.sincronizacaoreceita.ReceitaService;

import java.io.*;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UploadSemControler {
    private static final int POOL_SIZE = 5;
    private static final int SERVICE_MAX_TIMEOUT_MILIS = 5000;

    public void upload(String fileLoc){
        try {
            File arquivo = new File(fileLoc);
            int indiceBarra = arquivo.getPath().lastIndexOf("\\") + 1;
            if (indiceBarra == 0) {
                indiceBarra = arquivo.getPath().lastIndexOf("/") + 1;
            }
            // Basta pegar o substring com o caminho da pasta.
            String caminhoPasta = arquivo.getPath().substring(0, indiceBarra);

            FileWriter arq = new FileWriter(caminhoPasta+"/retorno.csv");
            PrintWriter gravarArq = new PrintWriter(arq);
            Scanner sc = new Scanner(arquivo);
            String line = sc.nextLine();
            gravarArq.println(line + ";retorno");

            //cria um pool de threads, se abrir uma thread para cada linha, pode háver sobrecarga no sistema
            //não utiliazar nenhuma tread aumenta muito o tempo de envio das linhas;
            ExecutorService tpes = Executors.newFixedThreadPool(POOL_SIZE);
            int numLines = 0;
            while (sc.hasNext()){
                //Sanitiza linha do arquivo
                line = sc.nextLine().trim();
                String[] lineInfo = line.split(";");
                String agencia = lineInfo[0];
                String conta = lineInfo[1].replace("-","");
                double saldo = Double.parseDouble(lineInfo[2].replace(",", "."));
                String status = lineInfo[3];
                String lines = line;

                //como o retorno das linhas no arquivos não precisa estar em ordem, grava no momneto de retorno do
                //serviço. Caso contrario seria criado uma List<Future> para inserir os valores quando todas as linhas
                //do csv fossem proecessadas
                tpes.submit(() -> {
                    boolean ret;
                    try {
                        var receitaService = new ReceitaService();
                        ret = receitaService.atualizarConta(agencia, conta, saldo, status);
                        gravarArq.println(lines + ";"+ ret);
                    } catch (Exception e) {
                            gravarArq.println(lines + ";erro");
                    }
                });
                numLines++;
            }

            //aguarda a conclusão reduzindo processamento, se utilizar estrutura while vai ter mais concorrencia com as
            //threds em execução.
            tpes.shutdown();
            tpes.awaitTermination(
                    (SERVICE_MAX_TIMEOUT_MILIS * numLines) / POOL_SIZE,
                    TimeUnit.MILLISECONDS);

            arq.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
