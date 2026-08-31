package org.alexis.ecommerceai.dto;

public class DiagnoseRequestDTO {
    private String problema;

    public DiagnoseRequestDTO() {
    }

    public DiagnoseRequestDTO(String problema) {
        this.problema = problema;
    }

    public String getProblema() {
        return problema;
    }

    public void setProblema(String problema) {
        this.problema = problema;
    }
}