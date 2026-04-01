package pt.unl.fct.di.adc.firstwebapp.util;

import java.util.Map;

public class UpdateData {
    public String username;
    public AttributesData attributes;

    public UpdateData() {}

    public static class AttributesData {
        public String username;
        public String phone;
        public String address;

        public AttributesData(){}
    }
}