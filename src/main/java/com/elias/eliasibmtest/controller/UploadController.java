package com.elias.eliasibmtest.controller;

import com.elias.eliasibmtest.sincronizacaoreceita.ReceitaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Controller
public class UploadController {
    private static final int POOL_SIZE = 5;
    private static final int SERVICE_MAX_TIMEOUT_MILIS = 5000;
    private static final int MAX_HOUR_SEND_FILE = 10;

    @RequestMapping("/")
    public String indexRedirect(){
        return "index.html";
    }

    @PostMapping("/")
    @ResponseBody
    public void upload(@RequestParam MultipartFile file, HttpServletResponse response){
        try {
            validateRequest(file);

            //init response
            response.setContentType("text/csv");
            response.setHeader("Content-disposition", "attachment; filename="+file.getOriginalFilename());

            var br = new BufferedReader(new InputStreamReader(file.getInputStream()));
            String line = br.readLine();
            response.getOutputStream().write((line + ";retorno\n").getBytes());

            //cria um pool de threads, se abrir uma thread para cada linha, pode háver sobrecarga no sistema
            //não utiliazar nenhuma tread aumenta muito o tempo de envio das linhas;
            ExecutorService tpes = Executors.newFixedThreadPool(POOL_SIZE);
            int numLines = 0;
            while ((line = br.readLine()) != null){
                //Sanitiza linha do arquivo
                line = line.trim();
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
                            response.getOutputStream().write((lines + ";" + ret + "\n").getBytes());
                        } catch (Exception e) {
                            try {
                                response.getOutputStream().write((lines + ";erro\n").getBytes());
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
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

            // envia retorno
            response.flushBuffer();
        }catch (Exception e){
            try {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        e.getMessage());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private void validateRequest(MultipartFile file) throws Exception {
        if(file == null){
            throw new Exception("Arquivo nulo");
        }
        if(!"text/csv".equals(file.getContentType())){
            throw new Exception("Tipo do arquivo não é CSV");
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm");
        if (sdf.getCalendar().get(Calendar.HOUR_OF_DAY) > MAX_HOUR_SEND_FILE){
            throw new Exception("Hora de envio excedida. Envio o arquivo até as " +
                    MAX_HOUR_SEND_FILE + "horas do dia corrente+");
        }
    }

}
