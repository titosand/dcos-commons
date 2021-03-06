package org.apache.mesos.curator;

import org.apache.mesos.state.SchemaVersionStore;
import org.apache.mesos.state.StateStoreException;
import org.apache.mesos.storage.CuratorPersister;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link SchemaVersionStore} which persists data in Zookeeper.
 */
public class CuratorSchemaVersionStore implements SchemaVersionStore {

    private static final Logger logger = LoggerFactory.getLogger(CuratorSchemaVersionStore.class);

    /**
     * Increment this whenever CuratorStateStore or CuratorConfigStore change in a way that
     * requires explicit migration.
     *
     * The migration implementation itself is not yet defined (let's wait until we need to actually
     * do it..)
     *
     * @see CuratorConfigStore#MIN_SUPPORTED_SCHEMA_VERSION
     * @see CuratorConfigStore#MAX_SUPPORTED_SCHEMA_VERSION
     * @see CuratorStateStore#MIN_SUPPORTED_SCHEMA_VERSION
     * @see CuratorStateStore#MAX_SUPPORTED_SCHEMA_VERSION
     */
    static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * This name/path must remain the same forever. It's the basis of all other migrations.
     * If it's not present, it's automatically created and initialized to the value of
     * {@code CURRENT_SCHEMA_VERSION}, on the assumption that the framework is being launched for
     * the first time.
     */
    static final String SCHEMA_VERSION_NAME = "SchemaVersion";

    private final CuratorPersister curator;
    private final String schemaVersionPath;

    public CuratorSchemaVersionStore(CuratorPersister curator, String rootPath) {
        this.curator = curator;
        this.schemaVersionPath = CuratorUtils.join(
                CuratorUtils.toServiceRootPath(rootPath), SCHEMA_VERSION_NAME);
    }

    public int fetch() throws StateStoreException {
        try {
            logger.debug("Fetching schema version from '{}'", schemaVersionPath);
            byte[] bytes = curator.fetch(schemaVersionPath);
            if (bytes.length == 0) {
                throw new StateStoreException(String.format(
                        "Invalid data when fetching schema version in '%s'", schemaVersionPath));
            }
            String rawString = CuratorUtils.deserialize(bytes);
            logger.debug("Schema version retrieved from '{}': {}", schemaVersionPath, rawString);
            try {
                return Integer.parseInt(rawString);
            } catch (NumberFormatException e) {
                throw new StateStoreException(String.format(
                        "Unable to parse fetched schema version: '%s' from path: %s",
                        rawString, schemaVersionPath), e);
            }
        } catch (KeeperException.NoNodeException e) {
            // The schema version doesn't exist yet. Initialize to the current version.
            logger.debug("Schema version not found at path: {}. New service install? " +
                    "Initializing path to schema version: {}.",
                    schemaVersionPath, CURRENT_SCHEMA_VERSION);
            store(CURRENT_SCHEMA_VERSION);
            return CURRENT_SCHEMA_VERSION;
        } catch (Exception e) {
            throw new StateStoreException("Storage error when fetching schema version", e);
        }
    }

    public void store(int version) throws StateStoreException {
        try {
            String versionStr = String.valueOf(version);
            logger.debug("Storing schema version: '{}' into path: {}",
                    versionStr, schemaVersionPath);
            curator.store(schemaVersionPath, CuratorUtils.serialize(versionStr));
        } catch (Exception e) {
            throw new StateStoreException(String.format(
                    "Storage error when storing schema version %d", version), e);
        }
    }
}
