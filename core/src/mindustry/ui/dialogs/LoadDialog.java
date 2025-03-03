package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.Saves.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.io.SaveIO.*;
import mindustry.ui.*;

import java.io.*;

import static mindustry.Vars.*;

public class LoadDialog extends BaseDialog{
    Table slots;
    String searchString;
    Gamemode filteredMode;
    TextField searchField;
    ScrollPane pane;

    public LoadDialog(){
        this("@loadgame");
    }

    public LoadDialog(String title){
        super(title);

        shown(() -> {
            searchString = "";
            setup();
        });
        hidden(() -> {
            if(slots != null) slots.clearChildren();
            control.saves.unload();
        });
        onResize(this::setup);

        addCloseButton();
        addSetup();
    }

    protected void setup(){
        cont.clear();

        slots = new Table();
        pane = new ScrollPane(slots);

        Table search = new Table();
        search.image(Icon.zoom);
        searchField = search.field("", t -> {
            searchString = t.length() > 0 ? t.toLowerCase() : null;
            rebuild();
        }).maxTextLength(50).growX().get();
        searchField.setMessageText("@save.search");
        for(Gamemode mode : Gamemode.all){
            TextureRegionDrawable icon = Vars.ui.getIcon("mode" + Strings.capitalize(mode.name()));
            boolean sandbox = mode == Gamemode.sandbox;
            if(Core.atlas.isFound(icon.getRegion()) || sandbox){
                search.button(sandbox ? Icon.terrain : icon, Styles.emptyTogglei, () -> {
                    filteredMode = filteredMode == mode ? null : mode;
                    rebuild();
                }).size(60f).checked(b -> filteredMode == mode).tooltip("@mode." + mode.name() + ".name");
            }
        }

        rebuild();

        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);

        cont.add(search).growX();
        cont.row();
        cont.add(pane).growY();
    }

    public void rebuild(){
        slots.clear();
        slots.marginRight(24).marginLeft(20f);

        Time.runTask(2f, () -> Core.scene.setScrollFocus(pane));

        int maxwidth = Math.max((int)(Core.graphics.getWidth() / Scl.scl(470)), 1);
        int i = 0;
        boolean any = false;

        for(SaveSlot slot : control.saves.getSaveSlots().sort(s -> -s.getTimestamp())){
            if(slot.isHidden()
            || (searchString != null && !Strings.stripColors(slot.getName()).toLowerCase().contains(searchString))
            || (filteredMode != null && filteredMode != slot.mode())){
                continue;
            }

            any = true;

            TextButton button = new TextButton("", Styles.grayt);
            button.getLabel().remove();
            button.clearChildren();

            button.defaults().left();

            button.table(title -> {
                title.add("[accent]" + slot.getName()).left().growX().width(230f).wrap();

                title.table(t -> {
                    t.right();
                    t.defaults().size(40f);

                    t.button(Icon.save, Styles.emptyTogglei, () -> {
                        slot.setAutosave(!slot.isAutosave());
                    }).checked(slot.isAutosave()).right();

                    t.button(Icon.trash, Styles.emptyi, () -> {
                        ui.showConfirm("@confirm", "@save.delete.confirm", () -> {
                            slot.delete();
                            rebuild();
                        });
                    }).right();

                    t.button(Icon.pencil, Styles.emptyi, () -> {
                        ui.showTextInput("@save.rename", "@save.rename.text", slot.getName(), text -> {
                            slot.setName(text);
                            rebuild();
                        });
                    }).right();

                    t.button(Icon.export, Styles.emptyi, () -> platform.export("save-" + slot.getName(), saveExtension, slot::exportFile)).right();

                }).padRight(-10).growX();
            }).growX().colspan(2);
            button.row();

            String color = "[lightgray]";
            TextureRegion def = Core.atlas.find("nomap");

            button.left().add(new BorderImage(def, 4f)).update(im -> {
                TextureRegionDrawable draw = (TextureRegionDrawable)im.getDrawable();
                if(draw.getRegion().texture.isDisposed()){
                    draw.setRegion(def);
                }

                var reg = slot.previewTexture();
                if(draw.getRegion() != reg){
                    draw.setRegion(reg);
                }
                im.setScaling(Scaling.fit);
            }).left().size(160f).padRight(6);

            button.table(meta -> {
                meta.left().top();
                meta.defaults().padBottom(-2).left().width(290f);
                meta.row();
                meta.labelWrap(Core.bundle.format("save.map", color + (slot.getMap() == null ? Core.bundle.get("unknown") : slot.getMap().name())));
                meta.row();
                meta.labelWrap(slot.mode().toString() + " /" + color + " " + Core.bundle.format("save.wave", color + slot.getWave()));
                meta.row();
                meta.labelWrap(() -> Core.bundle.format("save.autosave", color + Core.bundle.get(slot.isAutosave() ? "on" : "off")));
                meta.row();
                meta.labelWrap(() -> Core.bundle.format("save.playtime", color + slot.getPlayTime()));
                meta.row();
                meta.labelWrap(color + slot.getDate());
                meta.row();
            }).left().growX().width(250f);

            modifyButton(button, slot);

            slots.add(button).uniformX().fillX().pad(4).padRight(8f).margin(10f);

            if(++i % maxwidth == 0){
                slots.row();
            }
        }

        if(!any){
            slots.add("@save.none");
        }
    }

    public void addSetup(){

        buttons.button("@save.import", Icon.add, () -> {
            platform.showFileChooser(true, saveExtension, file -> {
                if(SaveIO.isSaveValid(file)){
                    try{
                        control.saves.importSave(file);
                        rebuild();
                    }catch(IOException e){
                        e.printStackTrace();
                        ui.showException("@save.import.fail", e);
                    }
                }else{
                    ui.showErrorMessage("@save.import.invalid");
                }
            });
        }).fillX().margin(10f);
    }

    public void runLoadSave(SaveSlot slot){
        slot.cautiousLoad(() -> ui.loadAnd(() -> {
            hide();
            ui.paused.hide();
            try{
                net.reset();
                slot.load();
                state.rules.editor = false;
                state.rules.sector = null;
                state.set(State.playing);
            }catch(SaveException e){
                Log.err(e);
                logic.reset();
                ui.showErrorMessage("@save.corrupted");
            }
        }));
    }

    public void modifyButton(TextButton button, SaveSlot slot){
        button.clicked(() -> {
            if(!button.childrenPressed()){
                runLoadSave(slot);
            }
        });
    }

    @Override
    public Dialog show(){
        super.show();

        if(Core.app.isDesktop() && searchField != null){
            Core.scene.setKeyboardFocus(searchField);
        }

        return this;
    }
}
