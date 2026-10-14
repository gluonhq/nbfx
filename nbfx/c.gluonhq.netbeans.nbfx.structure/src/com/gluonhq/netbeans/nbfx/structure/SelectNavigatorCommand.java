package com.gluonhq.netbeans.nbfx.structure;

import com.gluonhq.netbeans.nbfx.api.actions.AbstractCommand;
import com.gluonhq.netbeans.nbfx.api.actions.ActionIds;
import org.openide.util.NbBundle;

/**
 * Window ▸ Navigator: brings the Navigator view on screen. The menu entry itself lives in the layer
 * as {@code NbFx/Menus/Window/selectNavigator.ref} (registered on {@link StructureView}); this
 * command only carries the name and the behaviour.
 */
final class SelectNavigatorCommand extends AbstractCommand {

    SelectNavigatorCommand() {
        super(ActionIds.SELECT_NAVIGATOR,
                NbBundle.getMessage(SelectNavigatorCommand.class, "CTL_SelectNavigatorCommand"),
                null, false);
    }

    @Override
    public void run() {
        StructureView view = StructureView.instance();
        if (view != null) {
            view.show();
        }
    }
}