package ecs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.log4j.Logger;
import shared.HashingFunction.MD5;

import java.math.BigInteger;
import java.util.*;

/**
 * This class builds the Logical Hash Ring using a TreeMap
 * The hash ring is consisted of ECSNodes that can be configurable
 */
public class ECSHashRing {

    private Logger logger = Logger.getRootLogger();
    private TreeMap<BigInteger, ECSNode> activeNodes = new TreeMap<>();


    public ECSHashRing() {
    }

    // TODO: Check
    public ECSHashRing(String jsonData) {
        Collection<ECSNode> nodes = new Gson().fromJson(
                jsonData,
                new TypeToken<List<ECSNode>>() {
                }.getType());

        for (ECSNode node : nodes) {
            addNode(new ECSNode(node));
        }
    }

    public int getSize() {
        return this.activeNodes.size();
    }

    public TreeMap<BigInteger, ECSNode> getActiveNodes() {
        return activeNodes;
    }

    // TODO
//    public ECSNode getNodeByHostPort(ECSNode node) {
//        return null;
//
//    }

    // find the responsible server node, or the node matching the hash
    public ECSNode getNodeByHash(BigInteger hash) {
        if (this.activeNodes.size() == 0)
            return null;
        
        if (this.activeNodes.lastKey().compareTo(hash) < 0) {
            // return the first entry given the largest
            return this.activeNodes.firstEntry().getValue();
        }

        if (this.activeNodes.ceilingEntry(hash).getValue() == null) {
            logger.debug("[ECSHashRing] " + hash + " not found");
        }

        return this.activeNodes.ceilingEntry(hash).getValue();
    }

    public ECSNode getNodeByServerName(String name) {

        logger.debug("[ECSHashRing] getting node using " + name);

        if (this.activeNodes.size() == 0) {
            logger.debug("[ECSHashRing] ring size is 0!");
            return null;
        }

        for (Map.Entry<BigInteger, ECSNode> entry : activeNodes.entrySet()) {
            ECSNode node = entry.getValue();
            logger.debug("[ECSHashRing] current node: " + node.getNodeName());
            if (node.getNodeName().equals(name)) {
                return node;
            }
        }
        logger.warn("[ECSHashRing] node not found: " + name);
        return null;
    }


    public ECSNode getPrevNode(String hashName) {
        if (this.activeNodes.size() == 0)
            return null;
        BigInteger currKey = MD5.HashInBI(hashName);
        return getPrevNode(currKey);
    }

    public ECSNode getPrevNode(BigInteger currKey) {
        if (this.activeNodes.size() == 0) {
            return null;
        }

        if (this.activeNodes.firstKey().compareTo(currKey) > 0 || this.activeNodes.firstKey().compareTo(currKey) == 0) {
            // return the last entry given the smallest
            return this.activeNodes.lastEntry().getValue();
        }

        if (this.activeNodes.lowerEntry(currKey) == null) {
            logger.debug("[ECSHashRing] " + currKey + " not found");
        }

        return this.activeNodes.lowerEntry(currKey).getValue();

    }

    public ECSNode getNextNode(String hashName) {
        if (this.activeNodes.size() == 0)
            return null;
        BigInteger currKey = MD5.HashInBI(hashName);
        return getNextNode(currKey);
    }

    public ECSNode getNextNode(BigInteger currKey) {
        if (this.activeNodes.size() == 0)
            return null;
        if (this.activeNodes.lastKey().compareTo(currKey) < 0 ||
                this.activeNodes.lastKey().compareTo(currKey) == 0) {
            // return the first entry given the largest
            return this.activeNodes.firstEntry().getValue();
        }

        if (this.activeNodes.higherEntry(currKey) == null) {
            logger.debug("[ECSHashRing] " + currKey + " not found");
            logger.debug("[ECSHashRing]: " + this.activeNodes.keySet());
        }

        return this.activeNodes.higherEntry(currKey).getValue();

    }

    public void addNode(ECSNode node) {
        logger.debug("[ECSHashRing] Current ring size: " + this.activeNodes.size());
        logger.debug("[ECSHashRing] Adding node: " + node.getNodeName());
        printNode(node);

        ECSNode prevNode;
        ECSNode nextNode;

        // adding the first node
        if (getSize() == 0) {
            node.setHashRange(node.getNodeHash(), node.getNodeHash());
        } else if (getSize() == 1) {
            if (this.activeNodes.firstEntry().getKey().compareTo(node.getNodeHashBI()) == 0) {

                logger.error("[ECSHashRing] A collision on hash ring");
                return;

            } else if (this.activeNodes.firstEntry().getKey().compareTo(node.getNodeHashBI()) > 0) {

                nextNode = this.activeNodes.firstEntry().getValue();

                nextNode.setHashRange(node.getNodeHash(), nextNode.getNodeHash());
                node.setHashRange(nextNode.getNodeHash(), node.getNodeHash());
                this.activeNodes.put(nextNode.getNodeHashBI(), nextNode);

            } else {
                prevNode = this.activeNodes.firstEntry().getValue();

                prevNode.setHashRange(node.getNodeHash(), prevNode.getNodeHash());
                node.setHashRange(prevNode.getNodeHash(), node.getNodeHash());
                this.activeNodes.put(prevNode.getNodeHashBI(), prevNode);
            }
        } else {
            prevNode = this.getPrevNode(node.getNodeHash());
            if (prevNode != null) {
                node.setHashRange(prevNode.getNodeHash(), node.getNodeHash());
            }

            nextNode = this.getNextNode(node.getNodeHash());
            if (nextNode != null) {
                nextNode.setHashRange(node.getNodeHash(), nextNode.getNodeHash());
                this.activeNodes.put(nextNode.getNodeHashBI(), nextNode);
            }
        }

        this.activeNodes.put(node.getNodeHashBI(), node);

    }

    public String[] removeNode(ECSNode node) {

        if (node == null)
            return null;

        logger.debug("[ECSHashRing] Removing node:");

        printNode(node);

        assert this.getSize() > 0;

        String[] hashRange = node.getNodeHashRange();

        if (this.getSize() == 1) {
            logger.debug("[ECSHashRing] only one node in the ring!");
        } else {
            ECSNode prevNode = this.getPrevNode(node.getNodeHash());

            ECSNode nextNode = this.getNextNode(node.getNodeHash());

            if (prevNode != null && nextNode != null) {
                nextNode.setHashRange(prevNode.getNodeHash(), nextNode.getNodeHash());
                this.activeNodes.put(nextNode.getNodeHashBI(), nextNode);
            }
        }

        this.activeNodes.remove(MD5.HashInBI(node.getNodeHash()));
        return hashRange;
    }

    public void removeAllNode() {
        activeNodes.clear();
    }

    public void printNode(ECSNode node) {
        logger.debug("\t\tnode name: " + node.getNodeName());
        logger.debug("\t\tnode host: " + node.getNodeHost());
        logger.debug("\t\tnode hash: " + node.getNodeHash());
    }

    public void printAllNodes() {
        for (Map.Entry<BigInteger, ECSNode> entry : this.activeNodes.entrySet()) {
            ECSNode node = entry.getValue();

            logger.debug("\t\tnode name: " + node.getNodeName());
            logger.debug("\t\tprev node: " + getPrevNode(node.name).getNodeName());
            logger.debug("\t\tnext node: " + getNextNode(node.name).getNodeName());
            logger.debug("\t\tnode start hash: " + node.getNodeHashRange()[0]);
            logger.debug("\t\tnode end hash: " + node.getNodeHashRange()[1]);
            logger.debug("\t\t**************************************************");
        }
    }

    public String getHashRingJson() {
        List<ECSNode> activeNodes = new ArrayList<>(getActiveNodes().values());
        return new Gson().toJson(activeNodes);
    }
}
