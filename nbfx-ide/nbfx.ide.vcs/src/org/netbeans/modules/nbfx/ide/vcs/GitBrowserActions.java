/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.nbfx.ide.vcs;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.netbeans.api.nbfx.dialogs.ErrorReporter;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Branch;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Remote;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Revision;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Stash;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Tag;
import org.netbeans.api.nbfx.status.StatusMessageService;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The actions of the rows of the Repository Browser, as NetBeans has them. For a repository: Show
 * Changes, Commit All, Stash Changes, Create Branch and Create Tag of what is checked out, Remote &#9656; Fetch,
 * Pull and Push, and Open Configuration (Close Repository is the browser's own). For the rows below it: Checkout Revision, Create
 * Branch, Create Tag, Merge Revision, Rename Branch and Delete Branch for a branch; Show Details,
 * Checkout Revision, Create Branch, Merge Revision and Delete Tag for a tag; Stash Changes for the
 * stashes, and Apply Stash and Drop Stash for one of them; Add Remote for the remotes, and Fetch,
 * Pull, Push and Remove Remote for one of them. Each asks what it needs to
 * know, runs git off the FX thread ({@link GitBranches}, {@link GitStashes}, {@link GitRemotes}), says what git said when it failed, and has
 * the browser read the repository again.
 *
 * @since 1.5
 */
final class GitBrowserActions {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git-branch", 1);
    /** How much of the id of a commit is shown, as in NetBeans. */
    private static final int ID_LENGTH = 10;
    /** The date of a commit, as the user's locale writes one, in the user's time. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());

    /** Run on the FX thread after a command, whether or not it failed: something may have changed. */
    private final Runnable done;

    GitBrowserActions(Runnable done) {
        this.done = Objects.requireNonNull(done);
    }

    /**
     * The entries of the context menu of {@code branch}, a branch of {@code repository}. The branch
     * that is checked out is neither merged into itself nor deleted, and only a local branch is renamed.
     */
    List<MenuItem> items(GitRepositoryInfo repository, Branch branch) {
        MenuItem merge = item("Git.branch.merge", () -> merge(repository, branch));
        merge.setDisable(branch.active());
        MenuItem rename = item("Git.branch.rename", () -> rename(repository, branch));
        rename.setDisable(branch.remote());
        MenuItem delete = item("Git.branch.delete", () -> delete(repository, branch));
        delete.setDisable(branch.active());
        return List.of(item("Git.branch.checkout", () -> checkout(repository, branch)),
                item("Git.branch.create", () -> create(repository, branch)),
                item("Git.branch.tag", () -> tag(repository, branch)),
                merge, rename, delete);
    }

    /** The entries of the context menu of {@code tag}, a tag of {@code repository}. */
    List<MenuItem> items(GitRepositoryInfo repository, Tag tag) {
        return List.of(item("Git.tag.details", () -> details(repository, tag)),
                item("Git.branch.checkout", () -> checkout(repository, tag)),
                item("Git.branch.create", () -> create(repository, tag)),
                item("Git.branch.merge", () -> merge(repository, tag)),
                item("Git.tag.delete", () -> delete(repository, tag)));
    }

    /** The entries of the context menu of the stashes of {@code repository}. */
    List<MenuItem> stashItems(GitRepositoryInfo repository) {
        return List.of(item("Git.stash.save", () -> stash(repository)));
    }

    /** The entries of the context menu of {@code stash}, a stash of {@code repository}. */
    List<MenuItem> items(GitRepositoryInfo repository, Stash stash) {
        return List.of(item("Git.stash.apply", () -> apply(repository, stash)), new SeparatorMenuItem(),
                item("Git.stash.drop", () -> drop(repository, stash)));
    }

    /**
     * Stash Changes: saves the changes of the working tree as a stash and takes them out of it,
     * with the files git does not track yet if asked to; says so when there was nothing to save.
     */
    void stash(GitRepositoryInfo repository) {
        TextField text = nameField("");
        CheckBox untracked = new CheckBox(message("Git.stash.untracked"));
        Form form = new Form("Git.stash.save.title", null, null, null);
        form.title(NbBundle.getMessage(GitBrowserActions.class, "Git.stash.save.titleOf", repository.name()));
        form.add("Git.stash.message", text);
        form.add(untracked);
        if (form.confirmed(text)) {
            String said = text.getText();
            boolean all = untracked.isSelected();
            Path root = repository.root();
            RP.post(() -> {
                GitClient.Result result = GitStashes.save(root, said, all);
                FileUtil.refreshFor(root.toFile());
                Platform.runLater(() -> {
                    if (GitStashes.nothingSaved(result)) {
                        inform("Git.stash.save.title", message("Git.stash.nothing"));
                    }
                    finish("Git.stash.save.title", result);
                });
            });
        }
    }

    /** Apply Stash: puts the changes of the stash back in the working tree, and keeps the stash. */
    void apply(GitRepositoryInfo repository, Stash stash) {
        run("Git.stash.apply", repository, true, () -> GitStashes.apply(repository.root(), stash));
    }

    /** Drop Stash: drops the stash, whose changes are then lost. */
    void drop(GitRepositoryInfo repository, Stash stash) {
        if (confirmed("Git.stash.drop.title", NbBundle.getMessage(GitBrowserActions.class, "Git.stash.drop.question", stash.name()))) {
            run("Git.stash.drop.title", repository, false, () -> GitStashes.drop(repository.root(), stash));
        }
    }

    /**
     * The entries of the context menu of {@code repository}: what is done with it as a whole, and
     * with what is checked out. On no branch there is none to start a branch or a tag from by its
     * name, and without a branch tracked there is none to pull from or push to.
     */
    List<MenuItem> items(GitRepositoryInfo repository) {
        Branch branch = current(repository);
        boolean tracking = branch != null && branch.upstream() != null;
        MenuItem create = item("Git.branch.create", () -> create(repository, branch));
        create.setDisable(branch == null);
        MenuItem tag = item("Git.branch.tag", () -> tag(repository, branch));
        tag.setDisable(branch == null);
        MenuItem fetch = item("Git.remote.fetch", () -> fetch(repository));
        fetch.setDisable(repository.remotes().isEmpty());
        MenuItem pull = item("Git.remote.pull", () -> pull(repository));
        pull.setDisable(!tracking);
        MenuItem push = item("Git.remote.push", () -> push(repository));
        push.setDisable(!tracking);
        MenuItem showChanges = item("CTL_SelectGitCommand", () -> showChanges(repository));
        showChanges.setGraphic(GitIcons.view(GitIcons.SHOW_CHANGES));
        return List.of(showChanges, item("Git.commit.menu", () -> commitAll(repository)), new SeparatorMenuItem(),
                item("Git.stash.save", () -> stash(repository)), create, tag, new SeparatorMenuItem(),
                new Menu(message("Git.remote.menu"), null, fetch, pull, push), new SeparatorMenuItem(),
                item("Git.config.open", () -> openConfiguration(repository)));
    }

    /** Show Changes: opens the Git status window on the repository, or on the project in its folder. */
    void showChanges(GitRepositoryInfo repository) {
        GitStatusViewProvider view = GitStatusViewProvider.instance();
        FileObject folder = FileUtil.toFileObject(repository.root().toFile());
        if (view != null && folder != null) {
            view.show(folder);
        }
    }

    /**
     * Commit All: commits every change of the working tree, as the Git status window does for a
     * project, with the message asked for. It reads the changes first, off the FX thread: with none
     * there is nothing to commit, and with a conflicted file nothing is, as staging it would call
     * the conflict resolved.
     */
    void commitAll(GitRepositoryInfo repository) {
        Path root = repository.root();
        RP.post(() -> {
            VcsStatus status;
            String problem = null;
            try {
                status = GitClient.status(root);
            } catch (IOException | RuntimeException ex) {
                status = null;
                problem = ex.getMessage();
            }
            VcsStatus read = status;
            String said = problem;
            Platform.runLater(() -> {
                if (read == null) {
                    ErrorReporter.report(message("Git.commit.title"), message("Git.error"), said, null);
                } else if (read.hasConflicts()) {
                    inform("Git.commit.title", message("Git.commit.conflicts"));
                } else if (read.files(VcsCompareMode.HEAD_VS_WORKING_TREE).isEmpty()) {
                    inform("Git.commit.title", message("Git.status.clean.HEAD_VS_WORKING_TREE"));
                } else {
                    askAndCommit(repository, read.files(VcsCompareMode.HEAD_VS_WORKING_TREE).size());
                }
            });
        });
    }

    /** Asks for the message of a commit of {@code changes} files, and commits them. */
    private void askAndCommit(GitRepositoryInfo repository, int changes) {
        Label scope = new Label(NbBundle.getMessage(GitBrowserActions.class, "Git.commit.scope", changes, repository.name()));
        scope.setWrapText(true);
        scope.getStyleClass().add("git-commit-scope");
        TextArea text = new TextArea();
        text.setPromptText(message("Git.commit.message"));
        text.setPrefRowCount(5);
        text.setPrefColumnCount(36);
        text.setWrapText(true);
        GridPane.setVgrow(text, Priority.ALWAYS);
        Form form = new Form("Git.commit.title", null, null, null);
        form.add(scope);
        form.add(text);
        form.okText(message("Git.commit.all"));
        // Nothing to commit without a message.
        form.invalidWhen(text.textProperty().map(String::isBlank));
        if (form.confirmed(text)) {
            String said = text.getText().strip();
            // Read again by the commit: the changes may hold a conflict by now.
            run("Git.commit.title", repository, false, () -> GitClient.commitAll(repository.root(), said));
        }
    }

    /** Fetch: fetches from every remote of the repository. */
    void fetch(GitRepositoryInfo repository) {
        runRemote("Git.remote.fetch", "Git.remote.fetchingAll", "Git.remote.fetchedAll", repository, repository.name(), false,
                () -> GitRemotes.fetchAll(repository.root()));
    }

    /** Pull: merges into the branch that is checked out the one it tracks, once that is agreed to. */
    void pull(GitRepositoryInfo repository) {
        Branch branch = current(repository);
        if (branch != null && branch.upstream() != null && confirmed("Git.remote.pull.title", NbBundle.getMessage(
                GitBrowserActions.class, "Git.remote.pull.question", branch.upstream(), branch.name()))) {
            runRemote("Git.remote.pull.title", "Git.remote.pulling", "Git.remote.pulled", repository, branch.upstream(), true,
                    () -> GitRemotes.pull(repository.root()));
        }
    }

    /** Push: pushes the branch that is checked out to the one it tracks, once that is agreed to. */
    void push(GitRepositoryInfo repository) {
        Branch branch = current(repository);
        if (branch != null && branch.upstream() != null && confirmed("Git.remote.push.title", NbBundle.getMessage(
                GitBrowserActions.class, "Git.remote.push.question", branch.name(), branch.upstream()))) {
            runRemote("Git.remote.push.title", "Git.remote.pushing", "Git.remote.pushed", repository, branch.upstream(), false,
                    () -> GitRemotes.push(repository.root()));
        }
    }

    /** Open Configuration: opens the configuration file of the repository in the editor. */
    void openConfiguration(GitRepositoryInfo repository) {
        Path root = repository.root();
        RP.post(() -> {
            FileObject file;
            String problem = null;
            try {
                file = FileUtil.toFileObject(GitRefs.configFile(root).toFile());
            } catch (IOException | RuntimeException ex) {
                file = null;
                problem = ex.getMessage();
            }
            FileObject config = file;
            String said = problem;
            Platform.runLater(() -> {
                ContentManager content = Lookup.getDefault().lookup(ContentManager.class);
                if (config != null && config.isData() && content != null) {
                    content.openFile(config, null);
                } else if (said != null) {
                    ErrorReporter.report(message("Git.config.open"), message("Git.error"), said.strip(), null);
                }
            });
        });
    }

    /** The entries of the context menu of the remotes of {@code repository}. */
    List<MenuItem> remoteItems(GitRepositoryInfo repository) {
        return List.of(item("Git.remote.add", () -> addRemote(repository)));
    }

    /**
     * The entries of the context menu of {@code remote}, a remote of {@code repository}, or of an
     * address of it: of the one it is fetched from those that fetch, of the one it is pushed to the
     * one that pushes. There is nothing to pull into or to push while no branch is checked out.
     *
     * @param kind what the row is of: the remote, or one of its addresses
     */
    List<MenuItem> items(GitRepositoryInfo repository, Remote remote, RepositoryTree.Kind kind) {
        boolean detached = current(repository) == null;
        MenuItem fetch = item("Git.remote.fetch", () -> fetch(repository, remote));
        MenuItem pull = item("Git.remote.pull", () -> pull(repository, remote));
        pull.setDisable(detached);
        MenuItem push = item("Git.remote.push", () -> push(repository, remote));
        push.setDisable(detached);
        return switch (kind) {
            case FETCH_URL -> List.of(fetch, pull);
            case PUSH_URL -> List.of(push);
            default -> List.of(fetch, pull, push, new SeparatorMenuItem(),
                    item("Git.remote.remove", () -> remove(repository, remote)));
        };
    }

    /** Add Remote: names an address the repository is fetched from and pushed to. */
    void addRemote(GitRepositoryInfo repository) {
        // The first remote of a repository is called origin, by everyone.
        TextField name = nameField(repository.remotes().isEmpty() ? "origin" : "");
        TextField url = nameField("");
        url.setPrefColumnCount(36);
        Form form = new Form("Git.remote.add.title", null, null, null);
        form.add("Git.remote.name", name);
        form.add("Git.remote.url", url);
        form.invalidWhen(Bindings.createBooleanBinding(() -> name.getText().isBlank() || url.getText().isBlank(),
                name.textProperty(), url.textProperty()));
        if (form.confirmed(name.getText().isEmpty() ? name : url)) {
            String added = name.getText().strip();
            String address = url.getText().strip();
            run("Git.remote.add.title", repository, false, () -> GitRemotes.add(repository.root(), added, address));
        }
    }

    /** Fetch: fetches from the remote. */
    void fetch(GitRepositoryInfo repository, Remote remote) {
        runRemote("Git.remote.fetch", "Git.remote.fetching", "Git.remote.fetched", repository, remote.name(), false,
                () -> GitRemotes.fetch(repository.root(), remote));
    }

    /** Pull: fetches from the remote and merges into the branch that is checked out, once that is agreed to. */
    void pull(GitRepositoryInfo repository, Remote remote) {
        Branch branch = current(repository);
        if (branch != null && confirmed("Git.remote.pull.title", NbBundle.getMessage(GitBrowserActions.class,
                "Git.remote.pull.question", remote.name(), branch.name()))) {
            runRemote("Git.remote.pull.title", "Git.remote.pulling", "Git.remote.pulled", repository, remote.name(), true,
                    () -> GitRemotes.pull(repository.root(), remote));
        }
    }

    /** Push: pushes the branch that is checked out to the remote, once that is agreed to. */
    void push(GitRepositoryInfo repository, Remote remote) {
        Branch branch = current(repository);
        if (branch != null && confirmed("Git.remote.push.title", NbBundle.getMessage(GitBrowserActions.class,
                "Git.remote.push.question", branch.name(), remote.name()))) {
            runRemote("Git.remote.push.title", "Git.remote.pushing", "Git.remote.pushed", repository, remote.name(), false,
                    () -> GitRemotes.push(repository.root(), remote, branch));
        }
    }

    /** Remove Remote: forgets the remote, and what the repository knows of its branches. */
    void remove(GitRepositoryInfo repository, Remote remote) {
        if (confirmed("Git.remote.remove.title", NbBundle.getMessage(GitBrowserActions.class,
                "Git.remote.remove.question", remote.name()))) {
            run("Git.remote.remove.title", repository, false, () -> GitRemotes.remove(repository.root(), remote));
        }
    }

    /** The branch that is checked out in {@code repository}, or {@code null} on none. */
    private static Branch current(GitRepositoryInfo repository) {
        return repository.localBranches().stream().filter(Branch::active).findFirst().orElse(null);
    }

    /**
     * Runs {@code command}, which talks to a remote and takes its time, as {@link #run} does, with
     * the status bar telling that it runs and that it is done.
     *
     * @param runningKey what the status bar says meanwhile, of {@code from}
     * @param doneKey what it says once the command succeeded, of {@code from}
     * @param from what is fetched from or pushed to, as the status bar names it
     */
    private void runRemote(String titleKey, String runningKey, String doneKey, GitRepositoryInfo repository, String from,
            boolean changesFiles, Supplier<GitClient.Result> command) {
        StatusMessageService status = Lookup.getDefault().lookup(StatusMessageService.class);
        if (status != null) {
            status.setMessage(NbBundle.getMessage(GitBrowserActions.class, runningKey, from));
        }
        run(titleKey, repository, changesFiles, () -> {
            GitClient.Result result = command.get();
            if (status != null) {
                String said = result.exitCode() == 0
                        ? NbBundle.getMessage(GitBrowserActions.class, doneKey, from) : null;
                Platform.runLater(() -> status.setMessage(said));
            }
            return result;
        });
    }

    /**
     * Checkout Revision: checks the branch or the tag out, or a new branch started from it - which
     * is what is proposed for a remote branch that has no local one of its name yet.
     */
    void checkout(GitRepositoryInfo repository, Revision revision) {
        // A branch of a remote proposes a local one of its name, while there is none.
        String proposed = proposedName(repository, revision);
        boolean active = revision instanceof Branch branch && branch.active();
        CheckBox asNew = new CheckBox(message("Git.branch.checkout.asNew"));
        TextField name = nameField(revision instanceof Branch branch && branch.remote() ? branch.localName() : "");
        asNew.setSelected(!proposed.isEmpty());
        name.disableProperty().bind(asNew.selectedProperty().not());
        Form form = new Form("Git.branch.checkout.title", repository, revision, "Git.branch.checkout.prompt");
        form.add(asNew);
        form.add("Git.branch.name", name);
        // The branch that is checked out is only checked out as another one.
        form.invalidWhen(Bindings.createBooleanBinding(
                () -> asNew.isSelected() ? name.getText().isBlank() : active,
                asNew.selectedProperty(), name.textProperty()));
        if (form.confirmed(asNew.isSelected() ? name : asNew)) {
            boolean create = asNew.isSelected();
            String created = name.getText().strip();
            run("Git.branch.checkout.title", repository, true, () -> create
                    ? GitBranches.create(repository.root(), created, revision, true)
                    : GitBranches.checkout(repository.root(), revision));
        }
    }

    /** Create Branch: starts a branch where the branch or the tag is, and checks it out if asked to. */
    void create(GitRepositoryInfo repository, Revision revision) {
        TextField name = nameField(proposedName(repository, revision));
        CheckBox checkout = new CheckBox(message("Git.branch.create.checkout"));
        Form form = new Form("Git.branch.create.title", repository, revision, null);
        form.add("Git.branch.name", name);
        form.add(checkout);
        form.invalidWhen(name.textProperty().map(String::isBlank));
        if (form.confirmed(name)) {
            boolean switched = checkout.isSelected();
            String created = name.getText().strip();
            run("Git.branch.create.title", repository, switched,
                    () -> GitBranches.create(repository.root(), created, revision, switched));
        }
    }

    /** Create Tag: names the commit of the branch, with a message or without. */
    void tag(GitRepositoryInfo repository, Branch branch) {
        TextField name = nameField("");
        TextArea text = new TextArea();
        text.setPrefRowCount(4);
        text.setPrefColumnCount(28);
        text.setWrapText(true);
        Form form = new Form("Git.branch.tag.title", repository, branch, null);
        form.add("Git.tag.name", name);
        form.add("Git.tag.message", text);
        form.invalidWhen(name.textProperty().map(String::isBlank));
        if (form.confirmed(name)) {
            String created = name.getText().strip();
            String said = text.getText();
            run("Git.branch.tag.title", repository, false, () -> GitBranches.tag(repository.root(), created, branch, said));
        }
    }

    /** Merge Revision: says what the branch or the tag is, and merges it into what is checked out. */
    void merge(GitRepositoryInfo repository, Revision revision) {
        boolean active = revision instanceof Branch branch && branch.active();
        if (!active && new Form("Git.branch.merge.title", repository, revision, "Git.branch.merge.prompt")
                .confirmed(null)) {
            run("Git.branch.merge.title", repository, true, () -> GitBranches.merge(repository.root(), revision));
        }
    }

    /** Rename Branch: gives a local branch another name. */
    void rename(GitRepositoryInfo repository, Branch branch) {
        if (branch.remote()) {
            return;
        }
        TextField name = nameField(branch.name());
        Form form = new Form("Git.branch.rename.title", null, branch, null);
        form.add("Git.branch.rename.newName", name);
        form.invalidWhen(name.textProperty().map(text -> text.isBlank() || text.strip().equals(branch.name())));
        if (form.confirmed(name)) {
            String renamed = name.getText().strip();
            run("Git.branch.rename.title", repository, false, () -> GitBranches.rename(repository.root(), branch, renamed));
        }
    }

    /**
     * Delete Branch: deletes the branch, and asks again before it deletes one whose commits are in
     * no other branch.
     */
    void delete(GitRepositoryInfo repository, Branch branch) {
        if (branch.active() || !confirmed("Git.branch.delete.title",
                NbBundle.getMessage(GitBrowserActions.class, "Git.branch.delete.question", branch.name()))) {
            return;
        }
        Path root = repository.root();
        RP.post(() -> {
            GitClient.Result result = GitBranches.delete(root, branch, false);
            if (!GitBranches.notMerged(result)) {
                Platform.runLater(() -> finish("Git.branch.delete.title", result));
                return;
            }
            Platform.runLater(() -> {
                if (confirmed("Git.branch.delete.title", NbBundle.getMessage(GitBrowserActions.class,
                        "Git.branch.delete.notMerged", branch.name()))) {
                    run("Git.branch.delete.title", repository, false, () -> GitBranches.delete(root, branch, true));
                }
            });
        });
    }

    /** Delete Tag: deletes the tag, in this repository. */
    void delete(GitRepositoryInfo repository, Tag tag) {
        if (confirmed("Git.tag.delete.title", NbBundle.getMessage(GitBrowserActions.class, "Git.tag.delete.question", tag.name()))) {
            run("Git.tag.delete.title", repository, false, () -> GitBranches.deleteTag(repository.root(), tag));
        }
    }

    /**
     * Show Details: what the tag says of itself, if it was made with a message - who made it, when
     * and the message - and the commit it names.
     */
    void details(GitRepositoryInfo repository, Tag tag) {
        TextField id = Form.shown(new TextField(message("Git.revision.loading")));
        TextField tagger = Form.shown(new TextField(message("Git.revision.loading")));
        TextField date = Form.shown(new TextField(message("Git.revision.loading")));
        TextArea text = Form.shown(new TextArea(message("Git.revision.loading")));
        text.setPrefRowCount(3);
        text.setWrapText(true);
        Form form = new Form("Git.tag.details.title", repository, tag, null);
        form.add("Git.tag.id", id);
        form.add("Git.tag.tagger", tagger);
        form.add("Git.revision.date", date);
        form.add("Git.revision.message", text);
        form.closeOnly();
        Path root = repository.root();
        RP.post(() -> {
            GitRepositoryInfo.TagDetails details;
            String missing = message("Git.tag.plain");
            try {
                details = GitRefs.tagDetails(root, tag);
            } catch (IOException | RuntimeException ex) {
                details = null;
                missing = message("Git.revision.unknown");
            }
            GitRepositoryInfo.TagDetails read = details;
            String none = missing;
            Platform.runLater(() -> {
                // A tag without any of it says so once, where its message would be.
                id.setText(read == null ? "-" : read.id());
                tagger.setText(read == null ? "-" : read.tagger());
                date.setText(read == null ? "-" : DATE.format(read.date()));
                text.setText(read == null ? none : read.message());
            });
        });
        form.confirmed(null);
    }

    /**
     * Runs {@code command} off the FX thread, then says what failed, if it did, and has the browser
     * read again.
     *
     * @param changesFiles whether the command changes the working tree, whose files are then looked at again
     */
    void run(String titleKey, GitRepositoryInfo repository, boolean changesFiles, Supplier<GitClient.Result> command) {
        RP.post(() -> {
            GitClient.Result result = command.get();
            if (changesFiles) {
                // Changed behind the filesystem's back, also by a merge that stopped at a conflict.
                FileUtil.refreshFor(repository.root().toFile());
            }
            Platform.runLater(() -> finish(titleKey, result));
        });
    }

    private void finish(String titleKey, GitClient.Result result) {
        if (result.exitCode() != 0) {
            ErrorReporter.report(message(titleKey), message("Git.error"), result.output().strip(), null);
        }
        done.run();
    }

    /**
     * The name proposed for a branch started from {@code revision}: that of a branch of a remote,
     * while the repository has no branch of that name; else none.
     */
    private static String proposedName(GitRepositoryInfo repository, Revision revision) {
        return revision instanceof Branch branch && branch.remote() && !hasLocal(repository, branch.localName())
                ? branch.localName() : "";
    }

    private static boolean hasLocal(GitRepositoryInfo repository, String name) {
        return repository.localBranches().stream().anyMatch(local -> local.name().equals(name));
    }

    private static MenuItem item(String key, Runnable action) {
        MenuItem item = new MenuItem(message(key));
        item.setOnAction(event -> action.run());
        return item;
    }

    private static TextField nameField(String text) {
        TextField field = new TextField(text);
        field.setPrefColumnCount(28);
        return field;
    }

    private static boolean confirmed(String titleKey, String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(message(titleKey));
        alert.setHeaderText(null);
        own(alert);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /** Says {@code text}, which is no failure, and waits for it to be read. */
    private static void inform(String titleKey, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        alert.setTitle(message(titleKey));
        alert.setHeaderText(null);
        own(alert);
        alert.showAndWait();
    }

    private static void own(Dialog<?> dialog) {
        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitBrowserActions.class, key);
    }

    /**
     * A dialog that asks for what an action needs: the revision it is of, with the commit that is,
     * and the rows added below.
     */
    private static final class Form {

        private final Dialog<ButtonType> dialog = new Dialog<>();
        private final GridPane grid = new GridPane();
        private int rows;

        /**
         * @param repository the repository of {@code revision}, to tell its commit; {@code null} tells none
         * @param revision what the action is of, told first; {@code null} for an action of no revision
         * @param promptKey what the dialog says first, or {@code null}
         */
        Form(String titleKey, GitRepositoryInfo repository, Revision revision, String promptKey) {
            dialog.setTitle(message(titleKey));
            dialog.setResizable(true);
            own(dialog);
            DialogPane pane = dialog.getDialogPane();
            // The text areas and the padding are those of the Commit dialog.
            for (String sheet : List.of("vcs.css", "repository-browser.css")) {
                pane.getStylesheets().add(Objects.requireNonNull(GitBrowserActions.class.getResource(sheet)).toExternalForm());
            }
            pane.getStyleClass().addAll("git-commit", "git-branch-dialog");
            pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            grid.setHgap(8);
            grid.setVgap(8);
            ColumnConstraints labels = new ColumnConstraints();
            labels.setMinWidth(Region.USE_PREF_SIZE);
            ColumnConstraints fields = new ColumnConstraints();
            fields.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(labels, fields);
            pane.setContent(grid);
            if (promptKey != null) {
                grid.add(new Label(message(promptKey)), 0, rows++, 2, 1);
            }
            if (revision != null) {
                add("Git.branch.revision", shown(new TextField(revision.name())));
            }
            if (repository != null && revision != null) {
                grid.add(commit(repository.root(), revision), 0, rows++, 2, 1);
            }
        }

        /** The commit of {@code revision}, read off the FX thread: its id, author, date and message. */
        private static Node commit(Path root, Revision revision) {
            TextField id = shown(new TextField(message("Git.revision.loading")));
            TextField author = shown(new TextField(message("Git.revision.loading")));
            TextField date = shown(new TextField(message("Git.revision.loading")));
            TextArea text = shown(new TextArea(message("Git.revision.loading")));
            text.setPrefRowCount(4);
            text.setPrefColumnCount(36);
            text.setWrapText(true);
            GridPane box = new GridPane();
            box.getStyleClass().add("git-revision-box");
            box.setHgap(8);
            box.setVgap(6);
            ColumnConstraints labels = new ColumnConstraints();
            labels.setMinWidth(Region.USE_PREF_SIZE);
            ColumnConstraints fields = new ColumnConstraints();
            fields.setHgrow(Priority.ALWAYS);
            box.getColumnConstraints().addAll(labels, fields);
            int row = 0;
            for (Map.Entry<String, Control> field : List.of(Map.entry("Git.revision.id", (Control) id),
                    Map.entry("Git.revision.author", (Control) author), Map.entry("Git.revision.date", (Control) date),
                    Map.entry("Git.revision.message", (Control) text))) {
                Label label = new Label(message(field.getKey()));
                label.setLabelFor(field.getValue());
                GridPane.setValignment(label, field.getValue() == text ? VPos.TOP : VPos.CENTER);
                box.addRow(row++, label, field.getValue());
            }
            GridPane.setVgrow(text, Priority.ALWAYS);
            GridPane.setVgrow(box, Priority.ALWAYS);
            RP.post(() -> {
                GitRepositoryInfo.Commit commit;
                try {
                    commit = GitRefs.commit(root, revision.ref());
                } catch (IOException | RuntimeException ex) {
                    commit = null;
                }
                GitRepositoryInfo.Commit read = commit;
                Platform.runLater(() -> {
                    String unknown = message("Git.revision.unknown");
                    id.setText(read == null ? unknown : NbBundle.getMessage(GitBrowserActions.class, "Git.revision.id.value",
                            revision.name(), read.id().substring(0, Math.min(ID_LENGTH, read.id().length()))));
                    author.setText(read == null ? unknown : read.author());
                    date.setText(read == null ? unknown : DATE.format(read.date()));
                    text.setText(read == null ? unknown : read.message());
                });
            });
            return box;
        }

        /** A field that shows what is, and takes nothing: read, selected and copied, not changed. */
        static <C extends TextInputControl> C shown(C field) {
            field.setEditable(false);
            field.getStyleClass().add("git-revision-info");
            return field;
        }

        /** Adds {@code field} with the label of {@code labelKey} before it. */
        void add(String labelKey, Node field) {
            Label label = new Label(message(labelKey));
            label.setLabelFor(field);
            if (field instanceof TextArea) {
                GridPane.setValignment(label, VPos.TOP);
            }
            grid.addRow(rows++, label, field);
        }

        /** Adds {@code field}, which says itself what it is, below the fields. */
        void add(Node field) {
            grid.add(field, 0, rows++, 2, 1);
        }

        /** Calls the dialog {@code title}, where its name alone does not say what it is of. */
        void title(String title) {
            dialog.setTitle(title);
        }

        /** Calls OK {@code text}, where that says better what choosing it does. */
        void okText(String text) {
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText(text);
        }

        /** There is nothing to choose: the dialog tells, and is closed. */
        void closeOnly() {
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        }

        /** Nothing is done while {@code invalid} holds: OK is not to be chosen. */
        void invalidWhen(ObservableValue<Boolean> invalid) {
            dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(invalid);
        }

        /**
         * Shows the dialog and tells whether OK was chosen.
         *
         * @param first what has the focus at first, or {@code null} for OK
         */
        boolean confirmed(Node first) {
            Node button = dialog.getDialogPane().lookupButton(
                    dialog.getDialogPane().getButtonTypes().contains(ButtonType.OK) ? ButtonType.OK : ButtonType.CLOSE);
            Node focused = first == null ? button : first;
            dialog.setOnShown(event -> {
                focused.requestFocus();
                if (focused instanceof TextField text) {
                    text.selectAll();
                }
            });
            Optional<ButtonType> chosen = dialog.showAndWait();
            return chosen.isPresent() && chosen.get() == ButtonType.OK;
        }
    }
}
