package com.conote.client.controller;

import com.conote.client.model.NoteModel;
import com.conote.common.enums.SharePermission;
import com.conote.client.model.ShareMember;
import com.conote.client.service.CoNoteStore;
import com.conote.client.util.IconFactory;
import com.conote.client.util.MotionSupport;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ShareDialogController {
  @FXML
  private Label noteTitleLabel;

  @FXML
  private TextField emailField;

  @FXML
  private ComboBox<SharePermission> permissionCombo;

  @FXML
  private VBox memberList;

  private Runnable onClose;
  private CoNoteStore store;
  private NoteModel note;

  @FXML
  private void initialize() {
    permissionCombo.getItems().setAll(SharePermission.values());
    permissionCombo.setValue(SharePermission.VIEW);
  }

  public void setOnClose(Runnable onClose) {
    this.onClose = onClose;
  }

  public void setStore(CoNoteStore store) {
    this.store = store;
  }

  public void setNote(NoteModel note) {
    this.note = note;
    noteTitleLabel.setText(note.getTitle() == null || note.getTitle().isBlank()
        ? "Untitled note"
        : note.getTitle());
    renderMembers();
  }

  @FXML
  private void inviteMember() {
    store.addShareMember(note, emailField.getText(), permissionCombo.getValue());
    emailField.clear();
    renderMembers();
  }

  @FXML
  private void copyLink() {
    ClipboardContent content = new ClipboardContent();
    content.putString("conote://note/" + note.getId());
    Clipboard.getSystemClipboard().setContent(content);
  }

  @FXML
  private void closeDialog(ActionEvent event) {
    event.consume();
    close();
  }

  private void renderMembers() {
    memberList.getChildren().clear();
    for (ShareMember member : note.getShareMembers()) {
      HBox row = new HBox(12);
      row.getStyleClass().add("share-row");

      Label avatar = new Label(member.getAvatarText());
      avatar.getStyleClass().add("share-avatar");

      VBox info = new VBox(2);
      Label name = new Label(member.getDisplayName());
      name.getStyleClass().add("share-name");
      Label email = new Label(member.getEmail());
      email.getStyleClass().add("share-email");
      info.getChildren().addAll(name, email);

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      Label permission = new Label(member.getPermission().name());
      permission.getStyleClass().add("share-permission");

      row.getChildren().addAll(avatar, info, spacer);

      boolean owner = !note.getShareMembers().isEmpty() && note.getShareMembers().get(0) == member;
      if (owner) {
        Label ownerBadge = new Label("OWNER");
        ownerBadge.getStyleClass().add("share-owner-badge");
        row.getChildren().add(ownerBadge);
      }

      row.getChildren().add(permission);

      if (!owner) {
        javafx.scene.control.Button remove = new javafx.scene.control.Button("x");
        remove.getStyleClass().add("mini-icon-button");
        IconFactory.apply(remove, "codicon-close", 12, "mini-action-icon");
        remove.setText(null);
        MotionSupport.installButtonMotion(remove);
        remove.setOnAction(event -> {
          store.removeShareMember(note, member);
          renderMembers();
        });
        row.getChildren().add(remove);
      }

      memberList.getChildren().add(row);
    }
  }

  private void close() {
    if (onClose != null) {
      onClose.run();
    }
  }
}
