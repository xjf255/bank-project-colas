package org.example.screen;

class TicketDisplayInfo {
    String numeroCompleto; // Ej: "C-001" o "S-001"
    String tipo; // "CAJA" o "SERVICIO" (o "ATENCIÓN" si así lo manejas)
    String estado; // "LLAMANDO" (podrías tener "EN ESPERA" si tuvieras otra lista)
    int moduloAtencion; // El número de caja/ventanilla que lo atiende

    public TicketDisplayInfo(String numeroCompleto, String tipo) {
        this.numeroCompleto = numeroCompleto;
        this.tipo = tipo;
        this.estado = "EN ESPERA"; // Estado inicial
        this.moduloAtencion = -1;
    }

    @Override
    public String toString() {
        // Formato para la lista de "últimos llamados" (labels 1-6)
        return String.format("%s   %s", numeroCompleto, tipo.equals("CAJA") ? "CAJA" : "ATENCIÓN");
    }
}