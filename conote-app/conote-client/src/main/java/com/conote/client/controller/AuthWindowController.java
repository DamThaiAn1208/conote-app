package com.conote.client.controller;

import com.conote.client.network.AuthClient;
import com.conote.client.network.RemoteOperationException;
import com.conote.client.network.TransportException;
import com.conote.common.dto.auth.AuthDtos;
import com.conote.common.dto.protocol.ErrorCode;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/** Login and registration view. All network requests complete off the JavaFX thread. */
public final class AuthWindowController {
  @FXML private Label headingLabel;
  @FXML private Label descriptionLabel;
  @FXML private TextField fullNameField;
  @FXML private TextField emailField;
  @FXML private PasswordField passwordField;
  @FXML private PasswordField confirmPasswordField;
  @FXML private Label statusLabel;
  @FXML private Button submitButton;
  @FXML private Button switchModeButton;
  @FXML private ProgressIndicator progressIndicator;
  @FXML private HBox windowControls;

  private AuthClient authClient;
  private Consumer<AuthDtos.SessionView> onAuthenticated;
  private boolean registerMode;
  private boolean pending;
  private double dragX;
  private double dragY;

  @FXML
  private void startWindowDrag(MouseEvent event) {
    Stage stage = (Stage) windowControls.getScene().getWindow();
    dragX = event.getScreenX() - stage.getX();
    dragY = event.getScreenY() - stage.getY();
  }

  @FXML
  private void dragWindow(MouseEvent event) {
    Stage stage = (Stage) windowControls.getScene().getWindow();
    stage.setX(event.getScreenX() - dragX);
    stage.setY(event.getScreenY() - dragY);
  }

  @FXML
  private void minimizeWindow() {
    ((Stage) windowControls.getScene().getWindow()).setIconified(true);
  }

  @FXML
  private void closeWindow() {
    ((Stage) windowControls.getScene().getWindow()).close();
  }

  public void setContext(AuthClient authClient,
                         Consumer<AuthDtos.SessionView> onAuthenticated) {
    this.authClient = Objects.requireNonNull(authClient, "authClient");
    this.onAuthenticated = Objects.requireNonNull(onAuthenticated, "onAuthenticated");
  }

  public void resetAfterLogout() {
    passwordField.clear();
    confirmPasswordField.clear();
    showStatus("Đã đăng xuất trên thiết bị này.", false);
    setRegisterMode(false);
  }

  @FXML
  private void switchMode() {
    if (!pending) {
      setRegisterMode(!registerMode);
      clearStatus();
      passwordField.clear();
      confirmPasswordField.clear();
    }
  }

  @FXML
  private void submit() {
    if (pending || authClient == null) {
      return;
    }
    String email = emailField.getText() == null ? "" : emailField.getText().trim();
    String password = passwordField.getText();
    if (email.isBlank() || !email.contains("@") || password == null || password.isBlank()) {
      showStatus("Hãy nhập email và mật khẩu hợp lệ.", true);
      return;
    }
    if (registerMode) {
      String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();
      if (fullName.isBlank() || password.length() < 6
          || !password.equals(confirmPasswordField.getText())) {
        showStatus("Điền họ tên, mật khẩu ít nhất 6 ký tự và nhập lại đúng mật khẩu.", true);
        return;
      }
      setPending(true);
      authClient.register(email, password, fullName).whenComplete((user, failure) ->
          Platform.runLater(() -> {
            setPending(false);
            if (failure != null) {
              showStatus(messageFor(failure), true);
              return;
            }
            setRegisterMode(false);
            passwordField.clear();
            confirmPasswordField.clear();
            showStatus("Đăng ký thành công. Hãy đăng nhập.", false);
          }));
      return;
    }

    setPending(true);
    authClient.login(email, password).whenComplete((session, failure) ->
        Platform.runLater(() -> {
          setPending(false);
          if (failure != null) {
            showStatus(messageFor(failure), true);
            return;
          }
          if (session == null || session.token() == null || session.token().isBlank()
              || session.user() == null || session.user().userId() < 1) {
            showStatus("Máy chủ trả về phiên đăng nhập không hợp lệ.", true);
            return;
          }
          passwordField.clear();
          clearStatus();
          onAuthenticated.accept(session);
        }));
  }

  private void setRegisterMode(boolean value) {
    registerMode = value;
    headingLabel.setText(value ? "Tạo tài khoản" : "Đăng nhập");
    descriptionLabel.setText(value ? "Tạo tài khoản để lưu và chia sẻ ghi chú."
        : "Mở ghi chú của tài khoản bạn.");
    fullNameField.setVisible(value);
    fullNameField.setManaged(value);
    confirmPasswordField.setVisible(value);
    confirmPasswordField.setManaged(value);
    submitButton.setText(value ? "Đăng ký" : "Đăng nhập");
    switchModeButton.setText(value ? "Đã có tài khoản? Đăng nhập"
        : "Chưa có tài khoản? Đăng ký");
  }

  private void setPending(boolean value) {
    pending = value;
    emailField.setDisable(value);
    passwordField.setDisable(value);
    fullNameField.setDisable(value);
    confirmPasswordField.setDisable(value);
    submitButton.setDisable(value);
    switchModeButton.setDisable(value);
    progressIndicator.setVisible(value);
    progressIndicator.setManaged(value);
    if (value) {
      showStatus("Đang kết nối...", false);
    }
  }

  private void clearStatus() {
    statusLabel.setText("");
    statusLabel.setVisible(false);
    statusLabel.setManaged(false);
    statusLabel.getStyleClass().remove("auth-status-error");
  }

  private void showStatus(String message, boolean error) {
    statusLabel.setText(message);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);
    statusLabel.getStyleClass().remove("auth-status-error");
    if (error) {
      statusLabel.getStyleClass().add("auth-status-error");
    }
  }

  private String messageFor(Throwable failure) {
    Throwable actual = failure instanceof CompletionException && failure.getCause() != null
        ? failure.getCause() : failure;
    if (actual instanceof TransportException) {
      return "Không kết nối được máy chủ. Kiểm tra kết nối rồi thử lại.";
    }
    if (actual instanceof RemoteOperationException remote) {
      if (remote.code() == ErrorCode.UNAUTHENTICATED) {
        return "Email hoặc mật khẩu không đúng.";
      }
      if (remote.code() == ErrorCode.CONFLICT) {
        return "Email này đã được đăng ký.";
      }
      if (remote.code() == ErrorCode.VALIDATION) {
        return "Thông tin chưa hợp lệ. Hãy kiểm tra và thử lại.";
      }
      if (remote.code() == ErrorCode.UNAVAILABLE) {
        return "Máy chủ đang tạm thời không khả dụng.";
      }
      return "Không thể hoàn tất yêu cầu. Hãy thử lại.";
    }
    return "Đã xảy ra lỗi. Hãy thử lại.";
  }
}
