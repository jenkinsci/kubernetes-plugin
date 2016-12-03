package org.jenkinsci.demo;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

public class PetClinicAppTest {

    @Test
    public void testApp() throws IOException {
        WebDriver webDriver = new FirefoxDriver();
        webDriver.get(getAppUrl());
        Assert.assertEquals("PetClinic :: a Spring Framework demonstration", webDriver.getTitle());
    }

    private String getAppUrl() {
        return "http://petclinic:8080/petclinic";
    }
}
