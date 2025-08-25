package com.sarinah.sales;

import org.conscrypt.Conscrypt;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.security.Security;

@SpringBootApplication
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
public class SalesApplication {
	static { try { Security.insertProviderAt(Conscrypt.newProvider(), 1); } catch (Throwable ignored) {} }

	public static void main(String[] args) {
		SpringApplication.run(SalesApplication.class, args);
	}

}
