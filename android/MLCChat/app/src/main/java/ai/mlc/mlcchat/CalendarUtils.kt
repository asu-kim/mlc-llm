import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.*

object CalendarUtils {

    fun fetchAllCalendarEvents(context: Context): List<String> {
        val events = mutableListOf<String>()

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART
        )

        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            null,            // No selection — fetch all
            null,            // No selectionArgs
            sortOrder
        )

        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)

            while (it.moveToNext()) {
                val title = it.getString(titleIdx)
                val startTimeMillis = it.getLong(startIdx)
                val startTime = Date(startTimeMillis)
                val formattedTime = SimpleDateFormat("EEE, MMM d yyyy h:mm a", Locale.getDefault()).format(startTime)
                events.add("$title at $formattedTime")
            }
        }

        return events
    }
}