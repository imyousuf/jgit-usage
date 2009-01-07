package com.smartitengineering.jgit.usage;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.FileTreeEntry;
import org.spearce.jgit.lib.GitIndex;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.Tree;
import org.spearce.jgit.lib.TreeEntry;
import org.spearce.jgit.revwalk.ObjectWalk;
import org.spearce.jgit.revwalk.RevObject;
import org.spearce.jgit.treewalk.filter.PathFilter;

/**
 * Hello world!
 *
 */
public class App {

    private static final String INIT_ID = "23";
    private static final int OBJECT_COUNT = 100;

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        performVersioningExperiment(false, false);
        System.out.println("Duration: " + (System.currentTimeMillis() -
            startTime) + "ms");
    }

    private static void commitSingleBook(Tree head,
                                         String isbn,
                                         final String xmlData,
                                         ObjectWriter objectWriter,
                                         GitIndex gitIndex,
                                         Repository repository)
        throws IOException {
        FileTreeEntry treeEntry;
        byte[] dataBytes;
        if (head.existsBlob(isbn)) {
            treeEntry = (FileTreeEntry) head.findBlobMember(isbn);
            String modifiedData =
                xmlData.replace("<name>\n\t\tTest" + "\n\t</name>",
                "<name>\n\t\tTest Updated @ " + new Date() + "\n\t</name>");
            modifiedData = modifiedData.replace("$ID$", isbn);
            dataBytes = modifiedData.getBytes();
        }
        else {
            treeEntry = head.addFile(isbn);
            String modifiedData = xmlData.replace("$ID$", isbn);
            dataBytes = modifiedData.getBytes();
        }
        treeEntry.setExecutable(false);
        ObjectId objectId = objectWriter.writeBlob(dataBytes);
        treeEntry.setId(objectId);
        gitIndex.readTree(head);
        gitIndex.write();
        ObjectId treeId = gitIndex.writeTree();
        head.setId(treeId);
        ObjectId currentHeadId =
            repository.resolve(Constants.HEAD);
        boolean commitAvailable = true;
        if (currentHeadId != null) {
            Commit headCommit = repository.mapCommit(currentHeadId);
            if (headCommit != null) {
                Tree headTree = headCommit.getTree();
                if (headTree != null && head.getId().equals(headTree.getId())) {
                    commitAvailable = false;
                }
            }
        }
        if (commitAvailable) {
            System.out.println("Commit.....");
            ObjectId[] parentIds;
            if (currentHeadId != null) {
                parentIds = new ObjectId[]{currentHeadId};
            }
            else {
                parentIds = new ObjectId[0];
            }
            Commit commit =
                new Commit(repository, parentIds);
            commit.setTree(head);
            commit.setTreeId(head.getId());
            PersonIdent person = new PersonIdent(repository);
            commit.setAuthor(person);
            commit.setCommitter(person);
            commit.setMessage(isbn + " Commit message");
            ObjectId newCommitId = objectWriter.writeCommit(commit);
            commit.setCommitId(newCommitId);
            System.out.println("Commit: " + commit);
            RefUpdate refUpdate =
                repository.updateRef(Constants.HEAD);
            refUpdate.setNewObjectId(commit.getCommitId());
            refUpdate.setRefLogMessage(commit.getMessage(), false);
            refUpdate.forceUpdate();
        }
    }

    private static Tree getHeadTree(Repository repository)
        throws IOException {
        Tree head = repository.mapTree(Constants.HEAD);
        if (head == null) {
            head = new Tree(repository);
        }
        return head;
    }

    private static void performVersioningExperiment(
        final boolean performCommitWalk,
        final boolean performObjectWalk) {

        final String xmlData =
            "<bookElement>\n\t<name>\n\t\tTest\n\t</name>\n\t" +
            "<isbn>\n\t\t$ID$\n\t</isbn>\n\t<summary>\n\t\tSummary for test." +
            "\n\t</summary>\n</bookElement>\n";
        Repository repository = null;

        //WRITE to REPOSITORY
        try {
            final File repoLocation =
                new File(
                "/home/imyousuf/projects/git-projs/test-repo/jgit-test/.git");
            repository = new Repository(repoLocation);
            if (!repoLocation.exists()) {
                repository.create();
            }
            GitIndex gitIndex = repository.getIndex();
            ObjectWriter objectWriter =
                new ObjectWriter(repository);
            Tree head = getHeadTree(repository);
            for (int i = 0;
                i < App.OBJECT_COUNT;
                ++i) {
                String isbn =
                    String.valueOf(Integer.parseInt(App.INIT_ID) + i);
                commitSingleBook(head, isbn, xmlData, objectWriter, gitIndex,
                    repository);
                head = getHeadTree(repository);
            }

            //READ from REPOSITORY
            Repository repo = repository;
            if (performCommitWalk) {
                ObjectId commitId = repo.resolve(Constants.HEAD);
                traverseCommit(commitId, repo);
            }

            //REV Walk
            if (performObjectWalk) {
                head = getHeadTree(repository);
                printRevisionsForObject(repo);
            }
            int rand = Math.abs(new Random().nextInt()) % App.OBJECT_COUNT;
            long startTime = System.currentTimeMillis();
            String testIsbn = String.valueOf(Integer.parseInt(INIT_ID) + rand);
            readRevisionsForObjectPath(repo, testIsbn);
            System.out.println("Duration for single object revisions for ISBN " +
                testIsbn + ": " + (System.currentTimeMillis() - startTime) +
                "ms");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private static void printRevisionsForObject(Repository repo)
        throws NumberFormatException,
               IOException {
        for (int i = 0; i < App.OBJECT_COUNT;
            ++i) {
            System.out.println("INDEX: " + i);
            String isbn =
                String.valueOf(Integer.parseInt(App.INIT_ID) + i);
            System.out.println("ISBN: " + isbn);
            readRevisionsForObjectPath(repo, isbn);
        }
    }

    private static void readRevisionsForObjectPath(Repository repo,
                                                   String isbn)
        throws IOException {
        ObjectWalk objectWalk = new ObjectWalk(repo);
        /*
         * Checks whether the Commit has the tree or not. It does not
         * check whether it has changed or not.
         */
        objectWalk.setTreeFilter(PathFilter.create(isbn));
        RevObject revObject = null;
        objectWalk.markStart(
            objectWalk.parseCommit(repo.resolve(Constants.HEAD)));
        Set<ObjectId> revisions =
            new HashSet<ObjectId>();
        do {
            if (revObject != null) {
                Commit revision = repo.mapCommit(revObject.getId());
                Tree versionTree = repo.mapTree(revision.getTreeId());
                if (versionTree.existsBlob(isbn)) {
                    revisions.add(versionTree.findBlobMember(isbn).getId());
                }
            }
            revObject = objectWalk.next();
        }
        while (revObject != null);
        System.out.println("Revisions ("+ revisions.size() +"): " + revisions);
    }

    private static void traverseCommit(ObjectId commitId,
                                       Repository repo)
        throws IOException,
               IOException {
        System.out.println("Commit: " + commitId.toString());
        Commit currentCommit = repo.mapCommit(commitId);
        Tree tree = repo.mapTree(currentCommit.getTreeId());
        traverseTree(tree);
        ObjectId[] parents = currentCommit.getParentIds();
        for (ObjectId commitObjectId : parents) {
            traverseCommit(commitObjectId, repo);
        }
    }

    private static void traverseTree(Tree tree)
        throws IOException {
        System.out.println("Tree: " + tree.getId().toString());
        for (TreeEntry entry : tree.members()) {
            System.out.println(entry.getFullName() + " " +
                entry.getId().toString());
            if (entry instanceof FileTreeEntry) {
                FileTreeEntry fileTreeEntry =
                    (FileTreeEntry) entry;
                ObjectLoader loader = fileTreeEntry.openReader();
                System.out.println(new String(loader.getBytes()));
            }
        }
    }
}
