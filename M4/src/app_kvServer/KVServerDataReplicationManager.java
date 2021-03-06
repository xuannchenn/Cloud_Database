package app_kvServer;

import ecs.ECSHashRing;
import ecs.ECSNode;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class KVServerDataReplicationManager {
    private Logger logger = Logger.getRootLogger();

    private ECSNode thisNode;
    private List<KVServerDataReplication> replicationList;
    private final String prompt = "[KVServerDRManagr] ";
    private boolean recover = false;
    private long lastCommitedLsn = 0;

    public KVServerDataReplicationManager(String name, String host, int port) {
        this.replicationList = new ArrayList<>();
        this.thisNode = new ECSNode(name, host, port);
    }

    public void update(ECSHashRing hashRing) throws IOException {
        ECSNode node = hashRing.getNodeByServerName(thisNode.getNodeName());

        if (node == null) {
            clear();
            return;
        }
        Collection<ECSNode> replicas = hashRing.getReplicas(node);
        List<KVServerDataReplication> toReplicate = new ArrayList<>();

        for (ECSNode n : replicas) {
            toReplicate.add(new KVServerDataReplication(n));
        }

        for (Iterator<KVServerDataReplication> it = replicationList.iterator(); it.hasNext(); ) {
            KVServerDataReplication r = it.next();

            //if (!toReplicate.contains(r)) {
            if (!containsReplica(toReplicate, r)) {
                logger.info(prompt + r.getServerName() + " disconnected from " + thisNode.getNodeName());
                r.disconnect();
                it.remove();
            }
        }

        for (KVServerDataReplication r : toReplicate) {
            if (!containsReplica(replicationList, r)) {
                //if (!replicationList.contains(r)) {
                logger.info(prompt + r.getServerName() + " connects to " + thisNode.getNodeName());
                r.connect();
                replicationList.add(r);
            }
        }
    }

    public void setRecoverMode(boolean rec) {
        recover = rec;
    }

    public void unsetRecoverMode() {
        recover = false;
    }

    public void commit(long lsn) {
        lastCommitedLsn = lsn;
        for (KVServerDataReplication r : replicationList) {
            r.commit(lastCommitedLsn);
        }
    }

    public boolean forward(String cmd, String k, String v, long ts, int port) throws IOException {
        boolean ret = true;
        for (KVServerDataReplication r : replicationList) {
            logger.debug(prompt + " data replication from " + this.thisNode.getNodeName() + " to " + r.getServerName());
            if (recover) {
                ret &= r.dataReplication(cmd, k, v, ts, port, true);
            } else {
                ret &= r.dataReplication(cmd, k, v, ts, port, false);
            }

            if (!ret) {
                logger.error("[KVServerDRManager] Failed to replicate from " + this.thisNode.getNodeName() + " to " + r.getServerName());
                return ret;
            }
        }
        return ret;
    }

    public boolean forward_rollback(String cmd, String k, String v, long ts, int port) throws IOException {
        boolean ret = true;
        for (KVServerDataReplication r : replicationList) {
            logger.debug(prompt + " data replication from " + this.thisNode.getNodeName() + " to " + r.getServerName());
            if (recover) {
                ret &= r.dataReplication(cmd, k, v, ts, port, true);
            } else {
                ret &= r.dataReplication(cmd, k, v, ts, port, false);
            }
        }
        if (!ret) {
            logger.error("[KVServerDRManager] Failed to replicate from " + this.thisNode.getNodeName());
            //return ret;
        }
        return ret;
    }

    public void clear() {
        for (KVServerDataReplication r : replicationList) {
            r.disconnect();
        }
        replicationList.clear();
    }


    private boolean containsReplica(List<KVServerDataReplication> list, KVServerDataReplication target) {
        for (KVServerDataReplication r : list) {
            if (r.getServerName().equals(target.getServerName())) {
                return true;
            }
        }
        return false;

    }

    public void updateLSNForNewReplica(long lsn, int target) {
        for (KVServerDataReplication r : replicationList) {
            if (r.getServerPort() == target) {
                r.commit(lsn);
            }
        }
    }

    public List<KVServerDataReplication> getReplicationList() {
        return replicationList;
    }
}
