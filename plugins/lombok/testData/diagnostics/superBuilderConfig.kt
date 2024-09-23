// FIR_IDENTICAL
// FILE: Vehicle.java

import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class Vehicle {
    private String make;
    private String model;
}

// FILE: Car.java

import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class Car extends Vehicle {
    private int numberOfDoors;
}

// FILE: test.kt
fun test() {
    val vehicleBuilder: Vehicle.SpecialVehicleSuperBuilder<*, *> = Vehicle.builder()
    val carSuperBuilder: Car.SpecialCarSuperBuilder<*, *> = Car.builder()
}

// FILE: lombok.config
lombok.builder.className=Special*SuperBuilder
