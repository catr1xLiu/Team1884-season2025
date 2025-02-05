// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.leds;

import static edu.wpi.first.wpilibj2.command.Commands.either;
import static edu.wpi.first.wpilibj2.command.Commands.repeatingSequence;
import static frc.robot.subsystems.leds.LEDConstants.LED_LENGTH;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.util.Color;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.littletonrobotics.junction.Logger;

public class LEDSubsystem extends SubsystemBase {
  private final LEDIO io;
  private final LEDIOInputsAutoLogged inputs = new LEDIOInputsAutoLogged();

  private double rainbowStart = 0;
  private double dashStart = 0;

  /** Creates a new LEDSubsystem. */
  public LEDSubsystem(LEDIO io) {
    this.io = io;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("LED", inputs);
  }

  private void setIndex(int i, Color color) {
    io.setLED(i, color);
  }

  private void setSolid(Color color) {
    io.setColor(color);
  }

  public Command setSolidCmd(Color color) {
    return this.run(() -> setSolid(color));
  }

  public Command setBlinkingCmd(Color onColor, Color offColor, double frequency) {
    return repeatingSequence(
        setSolidCmd(onColor).withTimeout(1.0 / frequency),
        setSolidCmd(offColor).withTimeout(1.0 / frequency));
  }

  /** Sets the first portion of the leds to a color, and the rest off */
  public Command setProgressCmd(Color color, DoubleSupplier progress) {
    return this.run(
        () -> {
          for (int i = 0; i < LED_LENGTH; i++) {
            setIndex(i, i < progress.getAsDouble() * LED_LENGTH ? color : Color.kBlack);
          }
        });
  }

  public Command setRainbowCmd() {
    return this.run(
        () -> {
          for (int i = 0; i < LED_LENGTH; i++) {
            setIndex(i, Color.fromHSV((int) rainbowStart % 180 + i, 255, 255));
          }
          rainbowStart += 1;
        });
  }

  public Command setRunAlongCmd(
      Supplier<Color> colorDash, Supplier<Color> colorBg, int dashLength, double frequency) {
    return this.run(
        () -> {
          setSolid(colorBg.get());
          for (int i = (int) dashStart; i < dashStart + dashLength; i++) {
            setIndex(i % LED_LENGTH, colorDash.get());
          }

          dashStart += LED_LENGTH * frequency * 0.020;
          dashStart %= LED_LENGTH;
        });
  }

  public Command defaultStateDisplayCmd(BooleanSupplier enabled, BooleanSupplier targetIsSpeaker) {
    return either(
            either(
                this.setBlinkingCmd(new Color("#ffff00"), new Color(), 10.0)
                    .until(() -> !targetIsSpeaker.getAsBoolean() || !enabled.getAsBoolean()),
                this.setBlinkingCmd(new Color("#ff7777"), new Color(), 10.0)
                    .until(() -> targetIsSpeaker.getAsBoolean() || !enabled.getAsBoolean()),
                targetIsSpeaker),
            this.setRunAlongCmd(
                    // Set color to be purple with a moving dash corresponding to alliance color
                    () -> {
                      if (DriverStation.getAlliance().isEmpty()) {
                        return new Color("#b59aff");
                      } else if (DriverStation.getAlliance().get() == DriverStation.Alliance.Red) {
                        return new Color("#ff0000");
                      } else { // Blue
                        return new Color("#0000ff");
                      }
                    },
                    () -> new Color("#350868"),
                    10,
                    1.0)
                .until(enabled),
            enabled)
        .ignoringDisable(true)
        .withInterruptBehavior(Command.InterruptionBehavior.kCancelSelf)
        .repeatedly();
  }
}
