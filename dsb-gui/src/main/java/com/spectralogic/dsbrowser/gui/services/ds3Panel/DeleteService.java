package com.spectralogic.dsbrowser.gui.services.ds3Panel;

import com.google.common.collect.ImmutableList;
import com.spectralogic.dsbrowser.api.services.logging.LogType;
import com.spectralogic.dsbrowser.api.services.logging.LoggingService;
import com.spectralogic.dsbrowser.gui.components.deletefiles.DeleteFilesPopup;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3PanelPresenter;
import com.spectralogic.dsbrowser.gui.components.ds3panel.ds3treetable.Ds3TreeTableValue;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.services.tasks.Ds3DeleteBucketTask;
import com.spectralogic.dsbrowser.gui.services.tasks.Ds3DeleteFilesTask;
import com.spectralogic.dsbrowser.gui.services.tasks.Ds3DeleteFolderTask;
import com.spectralogic.dsbrowser.gui.util.LazyAlert;
import com.spectralogic.dsbrowser.gui.util.RefreshCompleteViewWorker;
import com.spectralogic.dsbrowser.gui.util.StringConstants;
import com.spectralogic.dsbrowser.util.GuavaCollectors;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public final class DeleteService {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteService.class);

    private static final LazyAlert alert = new LazyAlert("Error");

    /**
     * Delete a Single Selected Spectra S3 bucket
     *
     * @param ds3Common ds3Common object
     * @param values    list of objects to be deleted
     */
    public static void deleteBucket(final Ds3Common ds3Common,
                                    final ImmutableList<TreeItem<Ds3TreeTableValue>> values,
                                    final Workers workers,
                                    final LoggingService loggingService,
                                    final ResourceBundle resourceBundle) {
        LOG.info("Got delete bucket event");

        final Ds3PanelPresenter ds3PanelPresenter = ds3Common.getDs3PanelPresenter();

        final Session currentSession = ds3Common.getCurrentSession();
        if (currentSession != null) {
            final ImmutableList<String> buckets = getBuckets(values);
            if (buckets.size() > 1) {
                loggingService.logMessage(resourceBundle.getString("multiBucketNotAllowed"), LogType.ERROR);
                LOG.info("The user selected objects from multiple buckets.  This is not allowed.");
                alert.showAlert(resourceBundle.getString("multiBucketNotAllowed"));
                return;
            }
            final Optional<TreeItem<Ds3TreeTableValue>> first = values.stream().findFirst();
            if (first.isPresent()) {
                final TreeItem<Ds3TreeTableValue> value = first.get();
                final String bucketName = value.getValue().getBucketName();
                if (!Ds3PanelService.checkIfBucketEmpty(bucketName, currentSession)) {
                    loggingService.logMessage(resourceBundle.getString("failedToDeleteBucket"), LogType.ERROR);
                    alert.showAlert(resourceBundle.getString("failedToDeleteBucket"));
                } else {
                    final Ds3DeleteBucketTask ds3DeleteBucketTask = new Ds3DeleteBucketTask(currentSession.getClient(), bucketName);
                    DeleteFilesPopup.show(ds3DeleteBucketTask, ds3Common);
                    ds3Common.getDs3TreeTableView().setRoot(new TreeItem<>());
                    RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers, loggingService);
                    ds3PanelPresenter.getDs3PathIndicator().setText(StringConstants.EMPTY_STRING);
                    ds3PanelPresenter.getDs3PathIndicatorTooltip().setText(StringConstants.EMPTY_STRING);
                }
            }
        } else {
            LOG.error("NULL Session when attempting to deleteBucket");
        }
    }

    /**
     * Delete a Single Selected Spectra S3 folder     *
     *
     * @param ds3Common ds3Common object
     * @param values    list of objects to be deleted
     */
    public static void deleteFolder(final Ds3Common ds3Common,
                                    final ImmutableList<TreeItem<Ds3TreeTableValue>> values,
                                    final LoggingService loggingService,
                                    final ResourceBundle resourceBundle) {
        LOG.info("Got delete folder event");

        final Ds3PanelPresenter ds3PanelPresenter = ds3Common.getDs3PanelPresenter();
        final Session currentSession = ds3Common.getCurrentSession();

        if (currentSession != null) {
            final ImmutableList<String> buckets = getBuckets(values);

            if (buckets.size() > 1) {
                loggingService.logMessage(resourceBundle.getString("multiBucketNotAllowed"), LogType.ERROR);
                LOG.info("The user selected objects from multiple buckets.  This is not allowed.");
                alert.showAlert(resourceBundle.getString("multiBucketNotAllowed"));
                return;
            }

            final Optional<TreeItem<Ds3TreeTableValue>> first = values.stream().findFirst();
            if (first.isPresent()) {
                final TreeItem<Ds3TreeTableValue> value = first.get();
                final Ds3DeleteFolderTask deleteFolderTask = new Ds3DeleteFolderTask(currentSession.getClient(),
                        value.getValue().getBucketName(), value.getValue().getFullName());

                DeleteFilesPopup.show(deleteFolderTask, ds3Common);
                ds3PanelPresenter.getDs3PathIndicator().setText(StringConstants.EMPTY_STRING);
                ds3PanelPresenter.getDs3PathIndicatorTooltip().setText(StringConstants.EMPTY_STRING);
            }
        }
    }

    /**
     * Delete files from BlackPearl bucket/folder
     *
     * @param ds3Common ds3Common object
     * @param values    list of objects to be deleted
     */
    public static void deleteFiles(final Ds3Common ds3Common, final ImmutableList<TreeItem<Ds3TreeTableValue>> values) {
        LOG.info("Got delete file(s) event");

        final ImmutableList<String> buckets = getBuckets(values);

        final ArrayList<Ds3TreeTableValue> filesToDelete = new ArrayList<>(values
                .stream()
                .map(TreeItem::getValue)
                .collect(Collectors.toList())
        );
        final Map<String, List<Ds3TreeTableValue>> bucketObjectsMap = filesToDelete.stream().collect(Collectors.groupingBy(Ds3TreeTableValue::getBucketName));

        final Ds3DeleteFilesTask ds3DeleteFilesTask = new Ds3DeleteFilesTask(
                ds3Common.getCurrentSession().getClient(), buckets, bucketObjectsMap);

        DeleteFilesPopup.show(ds3DeleteFilesTask, ds3Common);
    }

    public static void managePathIndicator(final Ds3Common ds3Common,
                                           final Workers workers,
                                           final LoggingService loggingService) {
        Platform.runLater(() -> {
            final TreeTableView<Ds3TreeTableValue> ds3TreeTable = ds3Common.getDs3TreeTableView();
            final ObservableList<TreeItem<Ds3TreeTableValue>> selectedItems = ds3TreeTable.getSelectionModel().getSelectedItems();

            if (selectedItems.stream().map(TreeItem::getValue).anyMatch(Ds3TreeTableValue::isSearchOn)) {
                ds3TreeTable.getRoot().getChildren().removeAll(selectedItems);
                ds3TreeTable.getSelectionModel().clearSelection();
            } else {
                try {
                    final TreeItem<Ds3TreeTableValue> selectedItem = ds3TreeTable.getSelectionModel().getSelectedItems().stream()
                            .findFirst().get().getParent();
                    if (ds3TreeTable.getRoot() == null || ds3TreeTable.getRoot().getValue() == null) {
                        ds3TreeTable.setRoot(ds3TreeTable.getRoot().getParent());
                        ds3TreeTable.getSelectionModel().clearSelection();
                        ds3Common.getDs3PanelPresenter().getDs3PathIndicator().setText(StringConstants.EMPTY_STRING);
                        ds3Common.getDs3PanelPresenter().getDs3PathIndicatorTooltip().setText(StringConstants.EMPTY_STRING);
                    } else {
                        ds3TreeTable.setRoot(selectedItem);
                    }
                    ds3TreeTable.getSelectionModel().select(selectedItem);

                } catch (final Exception e) {
                    LOG.error("No Item found", e);
                }
                ds3TreeTable.getSelectionModel().clearSelection();
                RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers, loggingService);
            }
        });
    }

    private static ImmutableList<String> getBuckets(final ImmutableList<TreeItem<Ds3TreeTableValue>> values) {
        return values.stream().map(TreeItem::getValue).map(Ds3TreeTableValue::getBucketName).distinct().collect
                (GuavaCollectors.immutableList());
    }
}