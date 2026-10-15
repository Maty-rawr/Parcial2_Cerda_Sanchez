package cl.duoc.backendskatesapp.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "skates")
public class Skate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El modelo es obligatorio")
    private String modelo;

    @NotBlank(message = "La marca es obligatoria")
    private String marca;

    @DecimalMin(value = "0.0", inclusive = false, message = "La medida debe ser mayor que cero")
    private Double medida;

    @DecimalMin(value = "0.0", inclusive = false, message = "El wheelbase debe ser mayor que cero")
    private Double wheelbase;

    @NotNull(message = "El stock es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    public Skate() {
    }

    public Skate(Long id, String modelo, String marca, Double medida, Double wheelbase, Integer stock) {
        this.id = id;
        this.modelo = modelo;
        this.marca = marca;
        this.medida = medida;
        this.wheelbase = wheelbase;
        this.stock = stock;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public Double getMedida() { return medida; }
    public void setMedida(Double medida) { this.medida = medida; }
    public Double getWheelbase() { return wheelbase; }
    public void setWheelbase(Double wheelbase) { this.wheelbase = wheelbase; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}