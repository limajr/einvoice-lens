package io.github.einvoicelens.app;

import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@com.vaadin.flow.component.dependency.StyleSheet(com.vaadin.flow.theme.lumo.Lumo.STYLESHEET)
@com.vaadin.flow.component.dependency.StyleSheet("styles/lens.css")
@com.vaadin.flow.component.page.Push
@SpringBootApplication
public class LensApplication implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(LensApplication.class, args);
    }
}
