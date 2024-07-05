
package uk.org.rc0.logmygsm

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import uk.org.rc0.logmygsm.Overlay.Recipe.Null
import kotlin.Throws

internal class Confirm(activity: Activity?, message: String?, private val victim: Callback) {
    internal interface Callback {
        fun do_when_confirmed()
    }

    init {
        val builder: AlertDialog.Builder
        builder = Builder(activity)
        builder.setMessage(message)
        builder.setPositiveButton("OK", object : OnClickListener() {
            fun onClick(dialog: DialogInterface?, id: Int) {
                victim.do_when_confirmed()
            }
        })
        builder.setNegativeButton("Cancel", object : OnClickListener() {
            fun onClick(dialog: DialogInterface?, id: Int) {
                return
            }
        })
        val the_dialog: AlertDialog = builder.create()
        the_dialog.show()
    }
}