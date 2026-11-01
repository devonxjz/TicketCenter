package vn.ticketscenter.event.service;

import vn.ticketscenter.event.dao.EventManagementRepository.*;
import vn.ticketscenter.storage.*;
import java.io.*;
import java.util.*;

/** Storage network calls never hold a SQL transaction; DB authorization is checked twice. */
public interface EventCoverService {
    @FunctionalInterface
    interface Orphans {
        void record(UUID event, StoredAsset asset);
    }

    Result upload(Actor actor, UUID event, String key, InputStream content) throws IOException;
}
