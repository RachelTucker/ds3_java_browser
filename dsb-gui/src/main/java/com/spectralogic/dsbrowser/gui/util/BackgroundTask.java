package com.spectralogic.dsbrowser.gui.util;

import com.spectralogic.dsbrowser.api.services.logging.LoggingService;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackgroundTask implements Runnable{

    private final static Logger LOG = LoggerFactory.getLogger(BackgroundTask.class);
    private final static Alert ALERT = new Alert(Alert.AlertType.INFORMATION);
    private final static long FIVE_MINUTE_DELAY_IN_MILLIS = 1000 * 60 * 5;

    private final Ds3Common ds3Common;
    private final Workers workers;
    private final LoggingService loggingService;

    private boolean isAlertDisplayed = false;

    public BackgroundTask(final Ds3Common ds3Common, final Workers workers, final LoggingService loggingService) {
        this.ds3Common = ds3Common;
        this.workers = workers;
        this.loggingService = loggingService;
        final Stage stage = (Stage) ALERT.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(ImageURLs.DEEPSTORAGEBROWSER));
    }

    @Override
    public void run() {
        ALERT.setHeaderText(null);
        ALERT.setTitle("Network connection error");
        while (true) {
            try {
                if (ds3Common.getCurrentSession().stream().findFirst().isPresent()) {
                    final Session session = ds3Common.getCurrentSession().stream().findFirst().get();
                    if (CheckNetwork.isReachable(session.getClient())) {
                        if (isAlertDisplayed) {
                            LOG.info("network is up");
                            Platform.runLater(() -> ParseJobInterruptionMap.refreshCompleteTreeTableView(ds3Common, workers, loggingService));
                            isAlertDisplayed = false;
                        }

                    } else {
                        LOG.error("network is not reachable");
                        if (!isAlertDisplayed) {
                            Platform.runLater(() -> {
                                final String msg = "Host " + session.getClient().getConnectionDetails().getEndpoint() + " is unreachable. Please check your connection";
                                dumpTheStack(msg);
                                ALERT.setContentText(msg);
                                ALERT.showAndWait();
                            });

                            isAlertDisplayed = true;
                        }
                    }
                } else {
                    LOG.error("No Connection..");
                }
                Thread.sleep(FIVE_MINUTE_DELAY_IN_MILLIS);
            } catch (final Throwable e) {
                LOG.error("Encountered an error when attempting to verify that the bp is reachable", e);
            }
        }
    }

    public static void dumpTheStack(final String msg) {
        final StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < elements.length; i++) {
            final StackTraceElement s = elements[i];
            LOG.info(msg + "====> \tat " + s.getClassName() + "." + s.getMethodName() + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
        }
    }
}
