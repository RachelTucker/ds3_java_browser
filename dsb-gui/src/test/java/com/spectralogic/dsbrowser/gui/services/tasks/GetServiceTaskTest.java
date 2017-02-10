package com.spectralogic.dsbrowser.gui.services.tasks;

import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.components.ds3panel.ds3treetable.Ds3TreeTableValue;
import com.spectralogic.dsbrowser.gui.components.newsession.NewSessionPresenter;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedCredentials;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedSession;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.TreeItem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GetServiceTaskTest {

    private final Workers workers = new Workers();
    private Session session;

    @Before
    public void setUp() throws Exception {
        new JFXPanel();
        Platform.runLater(() -> {
            final SavedSession savedSession = new SavedSession("Test1", "192.168.6.164", "8080",
                    null, new SavedCredentials("c3VsYWJoamFpbg==", "yVBAvWTG"), false);
            session = new NewSessionPresenter().createConnection(savedSession);
        });
    }

    @Test
    public void getServiceTask() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            final ObservableList<TreeItem<Ds3TreeTableValue>> observableList = FXCollections.observableArrayList();
            final GetServiceTask getServiceTask = new GetServiceTask(observableList, session, workers,
                    Mockito.mock(Ds3Common.class));
            workers.execute(getServiceTask);
            getServiceTask.setOnSucceeded(event -> {
                latch.countDown();
                assertTrue(true);
            });
            getServiceTask.setOnFailed(event -> {
                latch.countDown();
                fail();
            });
            getServiceTask.setOnCancelled(event -> {
                latch.countDown();
                fail();
            });
        });
        latch.await();
    }
}