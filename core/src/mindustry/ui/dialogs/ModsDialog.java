package mindustry.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.scene.style.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Http.*;
import arc.util.io.*;
import arc.util.serialization.*;
import arc.util.serialization.Jval.*;
import mindustry.*;
import mindustry.client.ui.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustry.ui.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class ModsDialog extends BaseDialog{
    private ObjectMap<String, TextureRegion> textureCache = new ObjectMap<>();

    private float modImportProgress;
    private String searchtxt = "";
    private @Nullable Seq<ModListing> modList;
    private boolean orderDate = true;
    private BaseDialog currentContent;

    private BaseDialog browser;
    private Table browserTable;
    private int prompted, expected;
    private boolean autoUpdating; // Whether mods are currently being auto updated
    private float scroll = 0f;

    public ModsDialog(){
        super("@mods");
        addCloseButton();

        browser = new BaseDialog("@mods.browser");

        browser.cont.table(table -> {
            table.left();
            table.image(Icon.zoom);
            table.field(searchtxt, res -> {
                searchtxt = res;
                rebuildBrowser();
            }).growX().get();
            table.button(Icon.list, Styles.emptyi, 32f, () -> {
                orderDate = !orderDate;
                rebuildBrowser();
            }).update(b -> b.getStyle().imageUp = (orderDate ? Icon.list : Icon.star)).size(40f).get()
            .addListener(new Tooltip(tip -> tip.label(() -> orderDate ? "@mods.browser.sortdate" : "@mods.browser.sortstars").left()));
        }).fillX().padBottom(4);

        browser.cont.row();
        browser.cont.pane(tablebrow -> {
            tablebrow.margin(10f).top();
            browserTable = tablebrow;
        }).scrollX(false);
        browser.addCloseButton();

        browser.onResize(this::rebuildBrowser);

        buttons.button("@mods.guide", Icon.link, () -> Core.app.openURI(modGuideURL)).size(210, 64f);

        if(!mobile){
            buttons.button("@mods.openfolder", Icon.link, () -> Core.app.openFolder(modDirectory.absolutePath()));
        }

        buttons.button("Update Active Mods", Icon.download, () -> mods.mods.each(m -> m.state == ModState.enabled, m -> githubImportMod(m.getRepo(), m.isJava(), null, null)));

        shown(this::setup);
        onResize(this::setup);

        Events.on(ResizeEvent.class, event -> {
            if(currentContent != null){
                currentContent.hide();
                currentContent = null;
            }
        });

        hidden(() -> {
            if(mods.requiresReload()){
                reload();
            }
        });

        // Client mod updater
        Events.on(EventType.ClientLoadEvent.class, event -> {
            if (mods.mods.contains(LoadedMod::enabled) && Core.settings.getInt("modautoupdate") != 0 && (Time.timeSinceMillis(settings.getLong("lastmodupdate", (long) Time.toHours + 1L)) > Time.toHours)) {
                autoUpdating = true;
                Log.debug("Checking for mod updates");
                Core.settings.put("lastmodupdate", Time.millis());
                var shuffled = mods.mods.copy();
                shuffled.shuffle();
                for (Mods.LoadedMod mod : shuffled) { // Use shuffled mod list, if the user has more than 30 active mods, this will ensure that each is checked at least somewhat frequently
                    if (!mod.enabled() || mod.getRepo() == null) continue;
                    if (expected >= 30) continue; // Only make up to 30 api requests
                    expected++;

                    githubImportMod(mod.getRepo(), mod.isJava(), null, mod.meta.version);
                }
            } else Log.debug("Not updating mods, updated too recently / auto update is disabled / no enabled mods.");
        });
    }

    void modError(Throwable error){
        ui.loadfrag.hide();

        if(error instanceof NoSuchMethodError || Strings.getCauses(error).contains(t -> t.getMessage() != null && (t.getMessage().contains("trust anchor") || t.getMessage().contains("SSL") || t.getMessage().contains("protocol")))){
            ui.showErrorMessage("@feature.unsupported");
        }else if(error instanceof HttpStatusException st){
            ui.showErrorMessage(Core.bundle.format("connectfail", Strings.capitalize(st.status.toString().toLowerCase())));
        }else{
            ui.showException(error);
        }
    }

    void getModList(Cons<Seq<ModListing>> listener){
        if(modList == null){
            Http.get("https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json", response -> {
                String strResult = response.getResultAsString();

                Core.app.post(() -> {
                    try{
                        modList = JsonIO.json.fromJson(Seq.class, ModListing.class, strResult);

                        var d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        Func<String, Date> parser = text -> {
                            try{
                                return d.parse(text);
                            }catch(Exception e){
                                return new Date();
                            }
                        };

                        modList.sortComparing(m -> parser.get(m.lastUpdated)).reverse();
                        listener.get(modList);
                    }catch(Exception e){
                        e.printStackTrace();
                        ui.showException(e);
                    }
                });
            }, error -> Core.app.post(() -> modError(error)));
        }else{
            listener.get(modList);
        }
    }

    void setup(){
        float h = 110f;
        float w = Math.min(Core.graphics.getWidth() / Scl.scl(1.05f), 520f);

        cont.clear();
        cont.defaults().width(Math.min(Core.graphics.getWidth() / Scl.scl(1.05f), 556f)).pad(4);
        cont.add("@mod.reloadrequired").visible(mods::requiresReload).center().get().setAlignment(Align.center);
        cont.row();

        cont.table(buttons -> {
            buttons.left().defaults().growX().height(60f).uniformX();

            TextButtonStyle style = Styles.flatBordert;
            float margin = 12f;

            buttons.button("@mod.import", Icon.add, style, () -> {
                BaseDialog dialog = new BaseDialog("@mod.import");

                TextButtonStyle bstyle = Styles.flatt;

                dialog.cont.table(Tex.button, t -> {
                    t.defaults().size(300f, 70f);
                    t.margin(12f);

                    t.button("@mod.import.file", Icon.file, bstyle, () -> {
                        dialog.hide();

                        platform.showMultiFileChooser(file -> {
                            try{
                                mods.importMod(file);
                                setup();
                            }catch(IOException e){
                                ui.showException(e);
                                Log.err(e);
                            }
                        }, "zip", "jar");
                    }).margin(12f);

                    t.row();

                    t.button("@mod.import.github", Icon.github, bstyle, () -> {
                        dialog.hide();

                        ui.showTextInput("@mod.import.github", "", 64, Core.settings.getString("lastmod", ""), text -> {
                            //clean up the text in case somebody inputs a URL or adds random spaces
                            text = text.trim().replace(" ", "");
                            if(text.startsWith("https://github.com/")) text = text.substring("https://github.com/".length());

                            Core.settings.put("lastmod", text);
                            //there's no good way to know if it's a java mod here, so assume it's not
                            githubImportMod(text, false, null, null);
                        });
                    }).margin(12f);
                });
                dialog.addCloseButton();

                dialog.show();

            }).margin(margin);

            buttons.button("@mods.browser", Icon.menu, style, this::showModBrowser).margin(margin);
        }).width(w);

        cont.row();

        if(!mods.list().isEmpty()){
            boolean[] anyDisabled = {false};
            Table[] pane = {null};

            Cons<String> rebuild = query -> {
                pane[0].clear();
                boolean any = false;
                for(LoadedMod item : mods.list()){
                    if(Strings.matches(query, item.meta.displayName())){
                        any = true;
                        if(!item.enabled() && !anyDisabled[0] && mods.list().size > 0){
                            anyDisabled[0] = true;
                            pane[0].row();
                            pane[0].image().growX().height(4f).pad(6f).color(Pal.gray).row();
                        }

                        pane[0].button(t -> {
                            t.top().left();
                            t.margin(12f);

                            t.defaults().left().top();
                            t.table(title1 -> {
                                title1.left();

                                title1.add(new BorderImage(){{
                                    if(item.iconTexture != null){
                                        setDrawable(new TextureRegion(item.iconTexture));
                                    }else{
                                        setDrawable(Tex.nomap);
                                    }
                                    border(Pal.accent);
                                }}).size(h - 8f).padTop(-8f).padLeft(-8f).padRight(8f);

                                title1.table(text -> {
                                    boolean hideDisabled = !item.isSupported() || item.hasUnmetDependencies() || item.hasContentErrors();
                                    String shortDesc = item.meta.shortDescription();

                                    text.add("[accent]" + Strings.stripColors(item.meta.displayName()) + "\n" +
                                        (shortDesc.length() > 0 ? "[lightgray]" + shortDesc + "\n" : "")
                                        //so does anybody care about version?
                                        //+ "[gray]v" + Strings.stripColors(trimText(item.meta.version)) + "\n"
                                        + (item.enabled() || hideDisabled ? "" : Core.bundle.get("mod.disabled") + ""))
                                    .wrap().top().width(300f).growX().left();

                                    text.row();

                                    if(item.isOutdated()){
                                        text.labelWrap("@mod.outdatedv7").growX();
                                        text.row();
                                    }else if(item.clientDisabled()){
                                        text.labelWrap("@mod.clientdisabled").growX();
                                        text.row();
                                    }else if(!item.isSupported()){
                                        text.labelWrap(Core.bundle.format("mod.requiresversion", item.meta.minGameVersion)).growX();
                                        text.row();
                                    }else if(item.hasUnmetDependencies()){
                                        text.labelWrap(Core.bundle.format("mod.missingdependencies", item.missingDependencies.toString(", "))).growX();
                                        t.row();
                                    }else if(item.hasContentErrors()){
                                        text.labelWrap("@mod.erroredcontent").growX();
                                        text.row();
                                    }else if(item.meta.hidden){
                                        text.labelWrap("@mod.multiplayer.compatible").growX();
                                        text.row();
                                    }
                                }).top().growX();

                                title1.add().growX();
                            }).growX().growY().left();

                            t.table(right -> {
                                right.right();
                                right.button(item.enabled() ? Icon.downOpen : Icon.upOpen, Styles.clearNonei, () -> {
                                    mods.setEnabled(item, !item.enabled());
                                    setup();
                                }).size(50f).disabled(!item.isSupported());

                                right.button(item.hasSteamID() ? Icon.link : Icon.trash, Styles.clearNonei, () -> {
                                    if(!item.hasSteamID()){
                                        ui.showConfirm("@confirm", "@mod.remove.confirm", () -> {
                                            mods.removeMod(item);
                                            setup();
                                        });
                                    }else{
                                        platform.viewListing(item);
                                    }
                                }).size(50f);

                                if(steam && !item.hasSteamID()){
                                    right.row();
                                    right.button(Icon.export, Styles.clearNonei, () -> {
                                        platform.publish(item);
                                    }).size(50f);
                                }
                            }).growX().right().padRight(-8f).padTop(-8f);
                        }, Styles.flatBordert, () -> showMod(item)).size(w, h).growX().pad(4f);
                        pane[0].row();
                    }
                }

                if(!any){
                    pane[0].add("@none.found").color(Color.lightGray).pad(4);
                }
            };

            if(!mobile || Core.graphics.isPortrait()){
                cont.table(search -> {
                    search.image(Icon.zoom).padRight(8f);
                    search.field("", rebuild).growX();
                }).fillX().padBottom(4);
            }

            cont.row();
            cont.pane(table1 -> {
                pane[0] = table1.margin(10f).top();
                rebuild.get("");
            }).scrollX(false).update(s -> scroll = s.getScrollY()).get().setScrollYForce(scroll);
        }else{
            cont.table(Styles.black6, t -> t.add("@mods.none")).height(80f);
        }

        cont.row();
    }

    private void reload(){
        ui.showInfoOnHidden("@mods.reloadexit", () -> {
            Log.info("Exiting to reload mods.");

            try{ // FINISHME: Standardize auto reboots
                Fi jar = Fi.get(ModsDialog.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
                Seq<String> args = Seq.with(javaPath);
                args.addAll(System.getProperties().entrySet().stream().map(it -> "-D" + it).toArray(String[]::new));
                if(OS.isMac) args.add("-XstartOnFirstThread");
                args.addAll("-jar", jar.absolutePath());

                Runtime.getRuntime().exec(args.toArray());
                Core.app.exit();
            }catch(Exception ignored){} // If we can't find java, just close the game
            Core.app.exit();
        });
    }

    private void showMod(LoadedMod mod){
        BaseDialog dialog = new BaseDialog(mod.meta.displayName());

        dialog.addCloseButton();

        if(!mobile){
            dialog.buttons.button("@mods.openfolder", Icon.link, () -> Core.app.openFolder(mod.file.absolutePath()));
        }

        if(mod.getRepo() != null){
            boolean showImport = !mod.hasSteamID();
            dialog.buttons.button("@mods.github.open", Icon.link, () -> Core.app.openURI("https://github.com/" + mod.getRepo()));
            if(mobile && showImport) dialog.buttons.row();
            if(showImport) dialog.buttons.button("@mods.browser.reinstall", Icon.download, () -> githubImportMod(mod.getRepo(), mod.isJava(), null, null));
        }

        dialog.cont.pane(desc -> {
            desc.center();
            desc.defaults().padTop(10).left();

            desc.add("@editor.name").padRight(10).color(Color.gray).padTop(0);
            desc.row();
            desc.add(mod.meta.displayName()).growX().wrap().padTop(2);
            desc.row();
            if(mod.meta.author != null){
                desc.add("@editor.author").padRight(10).color(Color.gray);
                desc.row();
                desc.add(mod.meta.author).growX().wrap().padTop(2);
                desc.row();
            }
            if(mod.meta.description != null){
                desc.add("@editor.description").padRight(10).color(Color.gray).top();
                desc.row();
                desc.add(mod.meta.description).growX().wrap().padTop(2);
                desc.row();
            }

        }).width(400f);

        Seq<UnlockableContent> all = Seq.with(content.getContentMap()).<Content>flatten().select(c -> c.minfo.mod == mod && c instanceof UnlockableContent).as();
        if(all.any()){
            dialog.cont.row();
            dialog.cont.button("@mods.viewcontent", Icon.book, () -> {
                BaseDialog d = new BaseDialog(mod.meta.displayName());
                d.cont.pane(cs -> {
                    int i = 0;
                    for(UnlockableContent c : all){
                        cs.button(new TextureRegionDrawable(c.uiIcon), Styles.flati, iconMed, () -> {
                            ui.content.show(c);
                        }).size(50f).with(im -> {
                            var click = im.getClickListener();
                            im.update(() -> im.getImage().color.lerp(!click.isOver() ? Color.lightGray : Color.white, 0.4f * Time.delta));

                        }).tooltip(c.localizedName);

                        if(++i % (int)Math.min(Core.graphics.getWidth() / Scl.scl(110), 14) == 0) cs.row();
                    }
                }).grow();
                d.addCloseButton();
                d.show();
                currentContent = d;
            }).size(300, 50).pad(4);
        }

        dialog.show();
    }

    private void showModBrowser(){
        rebuildBrowser();
        browser.show();
    }

    private void rebuildBrowser(){
        ObjectSet<String> installed = mods.list().map(m -> m.getRepo()).asSet();

        browserTable.clear();
        browserTable.add("@loading");

        int cols = (int)Math.max(Core.graphics.getWidth() / Scl.scl(480), 1);

        getModList(rlistings -> {
            browserTable.clear();
            int i = 0;

            var listings = rlistings;
            if(!orderDate){
                listings = rlistings.copy();
                listings.sortComparing(m1 -> -m1.stars);
            }

            for(ModListing mod : listings){
                if(((mod.hasJava || mod.hasScripts) && Vars.ios) ||
                    (!Strings.matches(searchtxt, mod.name) && !Strings.matches(searchtxt, mod.repo))
                    //hack, I'm basically testing if 135.10 >= modVersion, which is equivalent to modVersion >= 136
                    || (Version.isAtLeast(135, 10, mod.minGameVersion))
                ) continue;

                float s = 64f;

                browserTable.button(con -> {
                    con.margin(0f);
                    con.left();

                    String repo = mod.repo;
                    con.add(new BorderImage(){
                        TextureRegion last;

                        {
                            border(installed.contains(repo) ? Pal.accent : Color.lightGray);
                            setDrawable(Tex.nomap);
                            pad = Scl.scl(4f);
                        }

                        @Override
                        public void draw(){
                            super.draw();

                            //textures are only requested when the rendering happens; this assists with culling
                            if(!textureCache.containsKey(repo)){
                                textureCache.put(repo, last = Core.atlas.find("nomap"));
                                Http.get("https://raw.githubusercontent.com/Anuken/MindustryMods/master/icons/" + repo.replace("/", "_"), res -> {
                                    Pixmap pix = new Pixmap(res.getResult());
                                    Core.app.post(() -> {
                                        try{
                                            var tex = new Texture(pix);
                                            tex.setFilter(TextureFilter.linear);
                                            textureCache.put(repo, new TextureRegion(tex));
                                            pix.dispose();
                                        }catch(Exception e){
                                            Log.err(e);
                                        }
                                    });
                                }, err -> {});
                            }

                            var next = textureCache.get(repo);
                            if(last != next){
                                last = next;
                                setDrawable(next);
                            }
                        }
                    }).size(s).pad(4f * 2f);

                    con.add(
                    "[accent]" + mod.name.replace("\n", "") +
                    (installed.contains(mod.repo) ? "\n[lightgray]" + Core.bundle.get("mod.installed") : "") +
                    "\n[lightgray]\uE809 " + mod.stars +
                    (Version.isAtLeast(mod.minGameVersion) ? mod.subtitle == null ? "" : "\n[lightgray]" + Strings.truncate(mod.subtitle, maxModSubtitleLength) :
                    "\n" + Core.bundle.format("mod.requiresversion", mod.minGameVersion)))
                    .width(358f).wrap().grow().pad(4f, 2f, 4f, 6f).top().left().labelAlign(Align.topLeft);

                }, Styles.flatBordert, () -> {
                    var sel = new BaseDialog(mod.name);
                    sel.cont.pane(p -> p.add(mod.description + "\n\n[accent]" + Core.bundle.get("editor.author") + "[lightgray] " + mod.author)
                        .width(mobile ? 400f : 500f).wrap().pad(4f).labelAlign(Align.center, Align.left)).grow();
                    sel.buttons.defaults().size(150f, 54f).pad(2f);
                    sel.buttons.button("@back", Icon.left, () -> {
                        sel.clear();
                        sel.hide();
                    });

                    var found = mods.list().find(l -> mod.repo != null && mod.repo.equals(l.getRepo()));
                    sel.buttons.button(found == null ? "@mods.browser.add" : "@mods.browser.reinstall", Icon.download, () -> {
                        sel.hide();
                        githubImportMod(mod.repo, mod.hasJava, null, null);
                    });

                    if(Core.graphics.isPortrait()){
                        sel.buttons.row();
                    }

                    sel.buttons.button("@mods.github.open", Icon.link, () -> {
                        Core.app.openURI("https://github.com/" + mod.repo);
                    });
                    sel.buttons.button("@mods.browser.view-releases", Icon.zoom, () -> {
                        BaseDialog load = new BaseDialog("");
                        load.cont.add("[accent]Fetching Releases...");
                        load.show();
                        Http.get(ghApi + "/repos/" + mod.repo + "/releases", res -> {
                            var json = Jval.read(res.getResultAsString());
                            JsonArray releases = json.asArray();

                            Core.app.post(() -> {
                                load.hide();

                                if(releases.size == 0){
                                    ui.showInfo("@mods.browser.noreleases");
                                }else{
                                    sel.hide();
                                    var downloads = new BaseDialog("@mods.browser.releases");
                                    downloads.cont.pane(p -> {
                                        for(int j = 0; j < releases.size; j++){
                                            var release = releases.get(j);

                                            int index = j;
                                            p.table(((TextureRegionDrawable)Tex.whiteui).tint(Pal.darkestGray), t -> {
                                                t.add("[accent]" + release.getString("name") + (index == 0 ? " " + Core.bundle.get("mods.browser.latest") : "")).top().left().growX().wrap().pad(5f);
                                                t.row();
                                                t.add((release.getString("published_at")).substring(0, 10).replaceAll("-", "/")).top().left().growX().wrap().pad(5f).color(Color.gray);
                                                t.row();
                                                t.table(b -> {
                                                    b.defaults().size(150f, 54f).pad(2f);
                                                    b.button("@mods.github.open-release", Icon.link, () -> Core.app.openURI(release.getString("html_url")));
                                                    b.button("@mods.browser.add", Icon.download, () -> {
                                                        String releaseUrl = release.getString("url");
                                                        githubImportMod(mod.repo, mod.hasJava, releaseUrl.substring(releaseUrl.lastIndexOf("/") + 1));
                                                    });
                                                }).right();
                                            }).margin(5f).growX().pad(5f);

                                            if(j < releases.size - 1) p.row();
                                        }
                                    }).width(500f).scrollX(false).fillY();
                                    downloads.buttons.button("@back", Icon.left, () -> {
                                        downloads.clear();
                                        downloads.hide();
                                        sel.show();
                                    }).size(150f, 54f).pad(2f);
                                    downloads.keyDown(KeyCode.escape, downloads::hide);
                                    downloads.keyDown(KeyCode.back, downloads::hide);
                                    downloads.hidden(sel::show);
                                    downloads.show();
                                }
                            });
                        }, t -> Core.app.post(() -> {
                            modError(t);
                            load.hide();
                        }));
                    });
                    sel.keyDown(KeyCode.escape, sel::hide);
                    sel.keyDown(KeyCode.back, sel::hide);
                    sel.show();
                }).width(438f).pad(4).growX().left().height(s + 8*2f).fillY();

                if(++i % cols == 0) browserTable.row();
            }
        });
    }

    private void handleMod(String repo, HttpResponse result, @Nullable String prevVersion){
        ZipFi rootZip = null;
        try{
            var sourceFile = tmpDirectory.child(repo.replace("/", "") + ".zip");
            long len = result.getContentLength();
            Floatc cons = len <= 0 ? f -> {} : p -> modImportProgress = p;
            Streams.copyProgress(result.getResultAsStream(), sourceFile.write(false), len, 4096, cons);
            Fi zip = sourceFile.isDirectory() ? sourceFile : (rootZip = new ZipFi(sourceFile));

            if(zip.list().length == 1 && zip.list()[0].isDirectory()){
                zip = zip.list()[0];
            }

            ModMeta meta = mods.findMeta(zip);

            if(meta == null) Log.warn("Mod @ doesn't have a '[mod/plugin].[h]json' file, skipping.", sourceFile);

            if (meta == null || meta.version == null || !meta.version.equals(prevVersion)) {
                var mod = mods.importMod(zip);
                mod.setRepo(repo);
            }
            zip.delete();
            Core.app.post(() -> {
                try{
                    setup();
                    ui.loadfrag.hide();
                }catch(Throwable e){
                    ui.showException(e);
                }
            });
        }catch(Throwable e){
            if(rootZip != null) rootZip.delete();
            modError(e);
        }

        if (++prompted == expected) { // FINISHME: Awful
            Core.app.post(() -> { // THIS DOES NOT RUN ON THE MAIN THREAD OTHERWISE
                autoUpdating = false;
                if (mods.requiresReload()){
                    if (Core.settings.getInt("modautoupdate") == 2) {
                        try{
                            Fi file = Fi.get(ModsDialog.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
                            Runtime.getRuntime().exec(OS.isMac ? // FINISHME: Standardize auto reboots
                                new String[]{javaPath, "-XstartOnFirstThread", "-jar", file.absolutePath()} :
                                new String[]{javaPath, "-jar", file.absolutePath()}
                            );
                            Core.app.exit();
                        }catch(IOException | URISyntaxException e){
                            Core.app.post(() -> {
                                BaseDialog dialog = new BaseDialog("Java Moment");
                                dialog.cont.clearChildren();
                                dialog.cont.add("It seems that you don't have java installed, please click the button below then click the \"latest release\" button on the website.").row();
                                dialog.cont.button("Install Java", () -> Core.app.openURI("https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot")).size(210f, 64f);
                            });
                        }
                        reload();
                    }
                    new Toast(5f).add("[accent]Mod updates found, they will be installed after restart.");
                } else new Toast(5f).add("[accent]No mod updates found.");
            });
        }
    }

    private void importFail(Throwable t){
        if (!autoUpdating) Core.app.post(() -> modError(t));
        else Log.err("Mod Auto Update Error", t);
    }

    private void githubImportMod(String repo, boolean isJava, @Nullable String release){
        githubImportMod(repo, isJava, release, null);
    }

    private void githubImportMod(String repo, boolean isJava, @Nullable String release, @Nullable String prevVersion){
        modImportProgress = 0f;
        if(prevVersion == null) ui.loadfrag.show("@downloading");
        ui.loadfrag.setProgress(() -> modImportProgress);

        if(isJava){
            githubImportJavaMod(repo, release, prevVersion);
        }else{
            Http.get(ghApi + "/repos/" + repo, res -> {
                var json = Jval.read(res.getResultAsString());
                String mainBranch = json.getString("default_branch");
                String language = json.getString("language", "<none>");

                //this is a crude heuristic for class mods; only required for direct github import
                //TODO make a more reliable way to distinguish java mod repos
                if(language.equals("Java") || language.equals("Kotlin")){
                    githubImportJavaMod(repo, release, prevVersion);
                }else{
                    githubImportBranch(mainBranch, repo, release, prevVersion);
                }
            }, this::importFail);
        }
    }

    private void githubImportJavaMod(String repo, @Nullable String release, @Nullable String prevVersion){
        //grab latest release
        Http.get(ghApi + "/repos/" + repo + "/releases/" + (release == null ? "latest" : release), res -> {
            var json = Jval.read(res.getResultAsString());
            var assets = json.get("assets").asArray();

            //prioritize dexed jar, as that's what Sonnicon's mod template outputs
            var dexedAsset = assets.find(j -> j.getString("name").startsWith("dexed") && j.getString("name").endsWith(".jar"));
            var asset = dexedAsset == null ? assets.find(j -> j.getString("name").endsWith(".jar")) : dexedAsset;

            if(asset != null){
                //grab actual file
                var url = asset.getString("browser_download_url");

                Http.get(url, result -> handleMod(repo, result, prevVersion), this::importFail);
            }else{
                throw new ArcRuntimeException("No JAR file found in releases. Make sure you have a valid jar file in the mod's latest Github Release.");
            }
        }, this::importFail);
    }

    private void githubImportBranch(String branch, String repo, @Nullable String release, @Nullable String prevVersion){
        if(release != null) {
            Http.get(ghApi + "/repos/" + repo + "/releases/" + release, res -> {
                String zipUrl = Jval.read(res.getResultAsString()).getString("zipball_url");
                Http.get(zipUrl, loc -> {
                    if(loc.getHeader("Location") != null){
                        Http.get(loc.getHeader("Location"), result -> {
                            handleMod(repo, result, prevVersion);
                        }, this::importFail);
                    }else{
                        handleMod(repo, loc, prevVersion);
                    }
                }, this::importFail);
            });
        }else{
            Http.get(ghApi + "/repos/" + repo + "/zipball/" + branch, loc -> {
                if(loc.getHeader("Location") != null){
                    Http.get(loc.getHeader("Location"), result -> {
                        handleMod(repo, result, prevVersion);
                    }, this::importFail);
                }else{
                    handleMod(repo, loc, prevVersion);
                }
            }, this::importFail);
        }
    }
}
