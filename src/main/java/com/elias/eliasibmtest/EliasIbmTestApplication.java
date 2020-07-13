package com.elias.eliasibmtest;

import com.elias.eliasibmtest.controller.UploadSemControler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EliasIbmTestApplication {

	public static void main(String[] args) {
		if(args.length>0){
			new UploadSemControler().upload("/home/elias/Documentos/file.csv");
		}else{
			SpringApplication.run(EliasIbmTestApplication.class, args);
		}

	}

}
