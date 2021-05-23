package mindustry.client.ui

import arc.graphics.Color
import mindustry.client.ui.Graph.Datapoint.Companion.dpt
import mindustry.ui.dialogs.BaseDialog

class PowerGraphDialog : BaseDialog("powerstats") {
    init {
        cont.add(Graph(Graph.Series("test", Color.scarlet, Graph.Datapoint.DatapointArray(listOf(
                0f dpt 1f,
                1f dpt 2f,
                2f dpt 3f
            ))))).grow()
        addCloseButton()
    }
}
