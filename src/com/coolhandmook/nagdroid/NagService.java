package com.coolhandmook.nagdroid;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

public class NagService extends IntentService
{
	public final static String UPDATE = "com.coolhandmook.nagdroid.UPDATE";
	public final static String TRIGGER = "com.coolhandmook.nagdroid.TRIGGER";

	private Database database;
	
	public NagService()
	{
		super("NagIntentService");
	}
	
	@Override
	public void onCreate()
	{
		super.onCreate();

    	database = new Database(this);
	}
	
	@Override
	public void onDestroy()
	{
		database.close();
		super.onDestroy();
	}
	
	@Override
	protected void onHandleIntent(Intent intent)
	{
		List<Nag> nags = database.allNagsSortedByTime();
		if (intent.getAction() == TRIGGER)
		{
			launchApplicationsDueNow(nags);
		}
		updateAlarm(nags);
	}
	
	private void launchApplicationsDueNow(List<Nag> nags)
	{
		Calendar calendar = new GregorianCalendar();
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int minute = calendar.get(Calendar.MINUTE);
		
		Iterator<Nag> iterator = nags.iterator();
		while (iterator.hasNext())
		{
			Nag nag = iterator.next();
			if (hour == nag.hour && minute == nag.minute)
			{
				launchApplication(nag);
			}
		}
	}
	
	private void launchApplication(Nag nag)
	{
		PackageManager packageManager = getPackageManager();

		ApplicationInfo app = null;
		try { app = packageManager.getApplicationInfo(nag.packageName, 0); }
		catch (NameNotFoundException e) {}

		Intent applicationIntent = packageManager.getLaunchIntentForPackage(nag.packageName);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, applicationIntent, 0);

		Notification notification = new Notification(R.drawable.ic_launcher, null, System.currentTimeMillis());
		notification.setLatestEventInfo(getApplicationContext(),
										"Nag!",
										packageManager.getApplicationLabel(app),
										contentIntent);
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(nag.rowId, notification);
	}

	private void updateAlarm(List<Nag> nags)
	{
		if (nags != null)
		{
			Nag next = findNextNag(nags);
			if (next != null)
			{
				Intent trigger = new Intent(this, NagService.class);
				trigger.setAction(TRIGGER);
				AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
				PendingIntent pendingIntent = PendingIntent.getService(this, 0, trigger, 0);
				alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime(next), pendingIntent);
			}
		}
	}
	
	private long alarmTime(Nag nag)
	{
		Calendar calendar = new GregorianCalendar();
		long currentTime = calendar.getTimeInMillis();

		calendar.set(Calendar.HOUR_OF_DAY, nag.hour);
		calendar.set(Calendar.MINUTE, nag.minute);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		if (currentTime > calendar.getTimeInMillis())
		{
			calendar.roll(Calendar.DAY_OF_YEAR, 1);
		}
		
		return calendar.getTimeInMillis();
	}
	
	private Nag findNextNag(List<Nag> nags)
	{
		Nag result = null;

		if (nags != null)
		{
			Calendar calendar = new GregorianCalendar();
			int clockTime = (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE);

			Iterator<Nag> iterator = nags.iterator();
			while (iterator.hasNext())
			{
				Nag nag = iterator.next();
				int nagTime = (nag.hour * 60) + nag.minute;
				if (nagTime > clockTime)
				{
					result = nag;
					break;
				}
			}
			
			if (result == null && !nags.isEmpty())
			{
				result = nags.get(0);
			}
		}

		return result;
	}
}
