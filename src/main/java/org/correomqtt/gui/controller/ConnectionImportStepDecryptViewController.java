package org.correomqtt.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tooltip;
import org.correomqtt.business.dispatcher.ImportDecryptConnectionsDispatcher;
import org.correomqtt.business.dispatcher.ImportDecryptConnectionsObserver;
import org.correomqtt.business.model.ConnectionConfigDTO;
import org.correomqtt.business.model.ConnectionExportDTO;
import org.correomqtt.gui.business.ExportTaskFactory;
import org.correomqtt.gui.helper.AlertHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ResourceBundle;

public class ConnectionImportStepDecryptViewController extends BaseControllerImpl implements ConnectionImportStepController, ImportDecryptConnectionsObserver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionImportStepDecryptViewController.class);
    private static ResourceBundle resources;

    private final ConnectionImportStepDelegate delegate;
    @FXML
    public PasswordField passwordField;

    private static final String EXCLAMATION_CIRCLE_SOLID = "exclamationCircleSolid";

    public ConnectionImportStepDecryptViewController(ConnectionImportStepDelegate delegate) {
        this.delegate = delegate;
        ImportDecryptConnectionsDispatcher.getInstance().addObserver(this);
    }

    public static LoaderResult<ConnectionImportStepDecryptViewController> load(ConnectionImportStepDelegate delegate) {
        LoaderResult<ConnectionImportStepDecryptViewController> result = load(
                ConnectionImportStepDecryptViewController.class,
                "connectionImportStepDecrypt.fxml",
                () -> new ConnectionImportStepDecryptViewController(delegate));
        resources = result.getResourceBundle();
        return result;
    }

    public void onDecryptClicked() {
        if (passwordField.getText() == null) {
            passwordField.setTooltip(new Tooltip(resources.getString("passwordEmpty")));
            passwordField.getStyleClass().add(EXCLAMATION_CIRCLE_SOLID);
            return;
        }

        ConnectionExportDTO dto = this.delegate.getOriginalImportedDTO();
        ExportTaskFactory.decryptConnections(dto.getEncryptedData(), dto.getEncryptionType(), passwordField.getText());
    }

    @Override
    public void onDecryptSucceeded(List<ConnectionConfigDTO> decryptedConnectionList) {
        this.delegate.setOriginalImportedConnections(decryptedConnectionList);
        this.delegate.goStepConnections();
    }

    @Override
    public void onDecryptCancelled() {
        onDecryptFailed(null);
    }

    @Override
    public void onDecryptFailed(Throwable exception) {
        AlertHelper.warn(resources.getString("connectionImportDecryptFailedTitle"),
                resources.getString("connectionImportDecryptFailedDescription"));
        delegate.onCancelClicked();
    }

    public void onCancelClicked() {
        this.delegate.onCancelClicked();
    }

    @Override
    public void cleanUp() {
        ImportDecryptConnectionsDispatcher.getInstance().removeObserver(this);
    }

    @Override
    public void initFromWizard() {
        passwordField.clear();
    }

    public void onDecryptBackClicked() {
        this.delegate.goStepChooseFile();
    }
}