// Copyright 2021-2024 FRC 6328
// http://github.com/Mechanical-Advantage
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// version 3 as published by the Free Software Foundation or
// available in the root directory of this project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.

package frc.robot;

import static frc.robot.Config.Controllers.getDriverController;
import static frc.robot.Config.Subsystems.DRIVETRAIN_ENABLED;
import static frc.robot.GlobalConstants.MODE;
import static frc.robot.subsystems.swerve.SwerveConstants.*;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.OI.DriverMap;
import frc.robot.commands.DriveCommands;
import frc.robot.subsystems.swerve.GyroIO;
import frc.robot.subsystems.swerve.GyroIOPigeon2;
import frc.robot.subsystems.swerve.ModuleIO;
import frc.robot.subsystems.swerve.ModuleIOSim;
import frc.robot.subsystems.swerve.ModuleIOSpark;
import frc.robot.subsystems.swerve.SwerveSubsystem;
import frc.robot.subsystems.vision.*;
import frc.robot.subsystems.vision.apriltagvision.AprilTagVisionConstants;
import org.littletonrobotics.junction.networktables.LoggedDashboardChooser;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of the robot (including
 * subsystems, commands, and button mappings) should be declared here.
 */
public class RobotContainer {
  // Subsystems
  private final SwerveSubsystem drive;

  // Controller
  private final DriverMap driver = getDriverController();

  // Dashboard inputs
  private final LoggedDashboardChooser<Command> autoChooser;
  private final Vision vision;

  /** The container for the robot. Contains subsystems, OI devices, and commands. */
  public RobotContainer() {
    if (DRIVETRAIN_ENABLED) {
      switch (MODE) {
        case REAL:
          // Real robot, instantiate hardware IO implementations
          drive =
              new SwerveSubsystem(
                  new GyroIOPigeon2(),
                  new ModuleIOSpark(FRONT_LEFT),
                  new ModuleIOSpark(FRONT_RIGHT),
                  new ModuleIOSpark(BACK_LEFT),
                  new ModuleIOSpark(BACK_RIGHT));
          vision =
              new Vision(
                  drive,
                  new AprilTagVisionIOPhotonVision(
                      "leftcam", AprilTagVisionConstants.LEFT_CAM_CONSTANTS.robotToCamera()),
                  new AprilTagVisionIOPhotonVision(
                      "rightcam", AprilTagVisionConstants.RIGHT_CAM_CONSTANTS.robotToCamera()),
                  new GamePieceVisionIOLimelight("limelight", drive::getRotation));
          break;

        case SIM:
          // Sim robot, instantiate physics sim IO implementations
          drive =
              new SwerveSubsystem(
                  new GyroIO() {},
                  new ModuleIOSim(),
                  new ModuleIOSim(),
                  new ModuleIOSim(),
                  new ModuleIOSim());
          vision =
              new Vision(
                  drive,
                  new VisionIOPhotonVisionSim(
                      "leftcam",
                      AprilTagVisionConstants.LEFT_CAM_CONSTANTS.robotToCamera(),
                      drive::getPose),
                  new VisionIOPhotonVisionSim(
                      "rightcam",
                      AprilTagVisionConstants.RIGHT_CAM_CONSTANTS.robotToCamera(),
                      drive::getPose));
          break;

        default:
          // Replayed robot, disable IO implementations
          drive =
              new SwerveSubsystem(
                  new GyroIO() {},
                  new ModuleIO() {},
                  new ModuleIO() {},
                  new ModuleIO() {},
                  new ModuleIO() {});
          vision = new Vision(drive, new VisionIO() {}, new VisionIO() {});
          break;
      }

      // Set up auto routines
      autoChooser = new LoggedDashboardChooser<>("Auto Choices", AutoBuilder.buildAutoChooser());

      // Set up SysId routines
      autoChooser.addOption(
          "Drive Wheel Radius Characterization", DriveCommands.wheelRadiusCharacterization(drive));
      autoChooser.addOption(
          "Drive Simple FF Characterization", DriveCommands.feedforwardCharacterization(drive));
      autoChooser.addOption(
          "Drive SysId (Quasistatic Forward)",
          drive.sysIdQuasistatic(SysIdRoutine.Direction.kForward));
      autoChooser.addOption(
          "Drive SysId (Quasistatic Reverse)",
          drive.sysIdQuasistatic(SysIdRoutine.Direction.kReverse));
      autoChooser.addOption(
          "Drive SysId (Dynamic Forward)", drive.sysIdDynamic(SysIdRoutine.Direction.kForward));
      autoChooser.addOption(
          "Drive SysId (Dynamic Reverse)", drive.sysIdDynamic(SysIdRoutine.Direction.kReverse));

      // Configure the button bindings
      configureButtonBindings();

      // Register the auto commands
      registerAutoCommands();
    } else drive = null;
  }

  /**
   * Use this method to define your button->command mappings. Buttons can be created by
   * instantiating a {@link GenericHID} or one of its subclasses ({@link
   * edu.wpi.first.wpilibj.Joystick} or {@link XboxController}), and then passing it to a {@link
   * edu.wpi.first.wpilibj2.command.button.JoystickButton}.
   */
  private void configureButtonBindings() {
    // Default command, normal field-relative drive
    drive.setDefaultCommand(
        DriveCommands.joystickDrive(
            drive, driver.getXAxis(), driver.getYAxis(), driver.getRotAxis()));

    // Lock to 0° when A button is held
    driver
        .alignToSpeaker()
        .whileTrue(
            DriveCommands.joystickDriveAtAngle(
                drive, driver.getXAxis(), driver.getYAxis(), () -> new Rotation2d()));

    // Switch to X pattern when X button is pressed
    driver.stopWithX().onTrue(Commands.runOnce(drive::stopWithX, drive));

    // Reset gyro to 0° when B button is pressed
    driver
        .resetOdometry()
        .onTrue(
            Commands.runOnce(
                    () ->
                        drive.setPose(
                            new Pose2d(drive.getPose().getTranslation(), new Rotation2d())),
                    drive)
                .ignoringDisable(true));

    // align to coral station with position customization when LB is pressed
    driver
        .alignToGamePiece()
        .whileTrue(
            DriveCommands.chasePoseRobotRelativeCommandXOverride(
                drive, () -> new Pose2d(), driver.getYAxis()));
  }

  /** Write all the auto named commands here */
  private void registerAutoCommands() {
    /** Overriding commands */

    // overrides the x axis
    NamedCommands.registerCommand(
        "OverrideCoralOffset", DriveCommands.overridePathplannerCoralOffset(() -> 2.0));

    // clears all override commands in the x and y direction
    NamedCommands.registerCommand("Clear XY Override", DriveCommands.clearXYOverrides());

    /** Robot function commands */
  }

  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    return autoChooser.get();
  }
}
