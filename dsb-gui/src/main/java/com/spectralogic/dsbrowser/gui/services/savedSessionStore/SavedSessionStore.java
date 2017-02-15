package com.spectralogic.dsbrowser.gui.services.savedSessionStore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spectralogic.dsbrowser.gui.components.newsession.NewSessionModel;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Ds3SessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.util.JsonMapping;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SavedSessionStore {
    private final static Logger LOG = LoggerFactory.getLogger(SavedSessionStore.class);
    private final static Path PATH = Paths.get(System.getProperty("user.home"), ".dsbrowser", "sessions.json");

    private final ObservableList<SavedSession> sessions;

    private boolean dirty = false;

    private SavedSessionStore(final List<SavedSession> sessionList) {
        this.sessions = FXCollections.observableArrayList(sessionList);
        this.sessions.addListener((ListChangeListener<SavedSession>) c -> {
            if (c.next() && (c.wasAdded() || c.wasRemoved())) {
                dirty = true;
            }
        });
    }

    public static SavedSessionStore loadSavedSessionStore() throws IOException {
        final List<SavedSession> sessions;
        if (Files.exists(PATH)) {
            try (final InputStream inputStream = Files.newInputStream(PATH)) {
                final SerializedSessionStore store = JsonMapping.fromJson(inputStream, SerializedSessionStore.class);
                sessions = store.getSessions();
            }
        } else {
            LOG.info("Creating new empty saved session store");
            sessions = new ArrayList<>();
        }
        return new SavedSessionStore(sessions);
    }

    public static void saveSavedSessionStore(final SavedSessionStore sessionStore) throws IOException {
        if (sessionStore.dirty) {
            LOG.info("Session store was dirty, saving...");
            final SerializedSessionStore store = new SerializedSessionStore(sessionStore.sessions);
            if (!Files.exists(PATH.getParent())) {
                Files.createDirectories(PATH.getParent());
            }
            try (final OutputStream outputStream = Files.newOutputStream(PATH, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
                JsonMapping.toJson(outputStream, store);
            } catch (final Exception e) {
                LOG.error("unable to save dirty session", e);
            }
        }
    }

    public ObservableList<SavedSession> getSessions() {
        return sessions;
    }

    public int saveSession(final Session session) {
        int index;
        if (sessions.size() == 0) {
            this.sessions.add(new SavedSession(session.getSessionName(), session.getEndpoint(), session.getPortNo(), session.getProxyServer(),
                    SavedCredentials.fromCredentials(session.getClient().getConnectionDetails().getCredentials()), session.getDefaultSession()));
            index = 1;
        } else if (containsSessionName(sessions, session.getSessionName())) {
            final SavedSession savedSession = sessions.stream().filter(o -> o.getName().equals(session.getSessionName())).findFirst().get();
            if (isNewValuePresent(savedSession, session)) {
                index = sessions.indexOf(savedSession);
                this.sessions.remove(savedSession);
                this.sessions.add(index, new SavedSession(session.getSessionName(), session.getEndpoint(), session.getPortNo(), session.getProxyServer(),
                        SavedCredentials.fromCredentials(session.getClient().getConnectionDetails().getCredentials()), session.getDefaultSession()));
            } else {
                return -1;
            }

        } else if (!containsSessionName(sessions, session.getSessionName())) {
            this.sessions.add(new SavedSession(session.getSessionName(), session.getEndpoint(), session.getPortNo(), session.getProxyServer(),
                    SavedCredentials.fromCredentials(session.getClient().getConnectionDetails().getCredentials()), session.getDefaultSession()));
            index = sessions.size();
        } else {
            index = -2;
        }
        return index;
    }

    private boolean isNewValuePresent(final SavedSession savedSession, final Session session) {
        if (!savedSession.getName().equals(session.getSessionName()))
            return true;
        if (!savedSession.getCredentials().getAccessId().equals(session.getClient().getConnectionDetails().getCredentials().getClientId()))
            return true;
        if (!savedSession.getCredentials().getSecretKey().equals(session.getClient().getConnectionDetails().getCredentials().getKey()))
            return true;
        if (!savedSession.getEndpoint().equals(session.getEndpoint()))
            return true;
        if (!savedSession.getPortNo().equals(session.getPortNo()))
            return true;
        if (savedSession.getProxyServer() != null && !savedSession.getProxyServer().equals(session.getProxyServer())) {
            return true;
        } else if (session.getProxyServer() != null) {
            return true;
        }
        return savedSession.getDefaultSession() == null || !savedSession.getDefaultSession()
                .equals(session.getDefaultSession());
    }

    public boolean containsSessionName(final ObservableList<SavedSession> list, final String name) {
        return list.stream().anyMatch(o -> o.getName().equals(name));
    }

    public boolean containsNewSessionName(final ObservableList<Session> list, final String name) {
        return list.stream().anyMatch(o -> o.getSessionName().equals(name));
    }

    public void removeSession(final SavedSession sessionName) {
        this.sessions.remove(sessionName);
    }

    public void openDefaultSession(final Ds3SessionStore store) {
        //open default session when DSB launched
        try {
            final List<SavedSession> defaultSession = getSessions().stream().filter(item -> item.getDefaultSession() != null && item.getDefaultSession().equals(true)).collect(Collectors.toList());
            if (defaultSession.size() == 1) {
                final SavedSession savedSession = defaultSession.stream().findFirst().orElse(null);

                if (savedSession != null) {
                    final NewSessionModel newModel = new NewSessionModel();
                    newModel.setSessionName(savedSession.getName());
                    newModel.setDefaultSession(true);
                    newModel.setAccessKey(savedSession.getCredentials().getAccessId());
                    newModel.setSecretKey(savedSession.getCredentials().getSecretKey());
                    newModel.setEndpoint(savedSession.getEndpoint());
                    newModel.setPortno(savedSession.getPortNo());
                    newModel.setProxyServer(savedSession.getProxyServer());
                    store.addSession(newModel.toSession());
                }
            }
        } catch (final Exception e) {
            LOG.error("Encountered error fetching default session", e);
        }
    }

    private static class SerializedSessionStore {
        @JsonProperty("sessions")
        private final List<SavedSession> sessions;

        @JsonCreator
        private SerializedSessionStore(@JsonProperty("sessions") final List<SavedSession> sessions) {
            this.sessions = sessions;
        }

        public List<SavedSession> getSessions() {
            return sessions;
        }
    }
}
