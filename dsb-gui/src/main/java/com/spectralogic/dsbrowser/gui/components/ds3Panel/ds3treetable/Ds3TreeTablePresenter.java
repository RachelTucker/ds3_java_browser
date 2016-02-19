package com.spectralogic.dsbrowser.gui.components.ds3panel.ds3treetable;

import com.google.common.collect.ImmutableList;
import com.spectralogic.ds3client.commands.GetBucketsRequest;
import com.spectralogic.ds3client.commands.GetBucketsResponse;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.util.GuavaCollectors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class Ds3TreeTablePresenter implements Initializable {
    private final static Logger LOG = LoggerFactory.getLogger(Ds3TreeTablePresenter.class);

    @FXML
    TreeTableView<Ds3TreeTableValue> ds3TreeTable;

    @Inject
    Workers workers;

    @Inject
    Session session;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        try {
            LOG.info("Loading Ds3TreeTablePresenter with session " + session.getSessionName());

            initTreeTableView();


        } catch (final Throwable e) {
            LOG.error("Encountered error when creating Ds3TreeTablePresenter", e);
            throw e;
        }
    }

    private void initTreeTableView() {

        ds3TreeTable.setRowFactory(view -> {

            final TreeTableRow<Ds3TreeTableValue> row = new TreeTableRow<>();

            row.setOnDragOver(event -> {
                if (event.getGestureSource() != ds3TreeTable && event.getDragboard().hasFiles()) {
                    event.acceptTransferModes(TransferMode.COPY);
                }

                event.consume();
            });

            row.setOnDragEntered(event -> {
                final TreeItem<Ds3TreeTableValue> treeItem = row.getTreeItem();

                if(treeItem != null) {
                    if (!treeItem.isLeaf() && !treeItem.isExpanded()) {
                        LOG.info("Expanding closed row");
                        treeItem.setExpanded(true);
                    }

                    final InnerShadow is = new InnerShadow();
                    is.setOffsetY(1.0f);

                    row.setEffect(is);
                }
                event.consume();

            });

            row.setOnDragExited(event -> {
                row.setEffect(null);
                event.consume();
            });

            return row;
        });

        ds3TreeTable.setOnDragDropped(event -> {
            final Dragboard db = event.getDragboard();

            if (db.hasFiles()) {
                 db.getFiles().stream().map(File::toPath).forEach(path -> LOG.info("Drop Event contains: " + path.toString()));
            }
            event.consume();
        });

        final TreeItem<Ds3TreeTableValue> rootTreeItem = new TreeItem<>();
        rootTreeItem.setExpanded(true);
        ds3TreeTable.setShowRoot(false);

        final GetServiceTask getServiceTask = new GetServiceTask(rootTreeItem.getChildren());
        ds3TreeTable.setRoot(rootTreeItem);
        workers.execute(getServiceTask);
    }

    private class GetServiceTask extends Task<ObservableList<TreeItem<Ds3TreeTableValue>>> {

        private final ReadOnlyObjectWrapper<ObservableList<TreeItem<Ds3TreeTableValue>>> partialResults;

        public GetServiceTask(final ObservableList<TreeItem<Ds3TreeTableValue>> observableList) {

            partialResults = new ReadOnlyObjectWrapper<>(this, "partialResults", observableList);
        }

        public ObservableList<TreeItem<Ds3TreeTableValue>> getPartialResults() {
            return this.partialResults.get();
        }

        public ReadOnlyObjectProperty<ObservableList<TreeItem<Ds3TreeTableValue>>> partialResultsProperty() {
            return partialResults.getReadOnlyProperty();
        }

        @Override
        protected ObservableList<TreeItem<Ds3TreeTableValue>> call() throws Exception {
            final GetBucketsResponse response = session.getClient().getBuckets(new GetBucketsRequest());

            final ImmutableList<Ds3TreeTableValue> buckets = response.getListAllMyBucketsResult()
                    .getBuckets().stream()
                    .map(bucket -> new Ds3TreeTableValue(bucket.getName(), Ds3TreeTableValue.Type.BUCKET, 0, bucket.getCreationDate().toString()))
                    .collect(GuavaCollectors.immutableList());

            Platform.runLater(() -> buckets.stream().forEach(value -> partialResults.get().add(new Ds3TreeTableItem(value.getName(), session, value, workers))));

            return this.partialResults.get();
        }
    }
}
