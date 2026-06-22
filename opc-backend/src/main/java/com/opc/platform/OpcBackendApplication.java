package com.opc.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.opc.platform.**.mapper")
@SpringBootApplication
public class OpcBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpcBackendApplication.class, args);
	}

}
