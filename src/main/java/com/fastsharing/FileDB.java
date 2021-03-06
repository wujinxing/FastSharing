package com.fastsharing;

import java.util.ArrayList;
import java.util.Date;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;

import com.fastsharing.structure.TheFile;

/**
 * The file database's API
 * 
 * @author g2minhle
 * 
 */
public class FileDB {

	/** The initial user ID to avoid giving small ID (ie: 1,2,3) */
	private static int initialUserID;
	/** The session factory */
	private static SessionFactory factory;
	/** The total size of the database */
	private static long totalSize;
	/** The file size limit */
	private static long MAX_SIZE = 100000000;
	/** The memory limit */
	private static long MAX_CAP = 300000000;
	/** Constant indicate there is some problem */
	public static int ERROR_ERROR = -1;
	/** Constant indicate the file is too big */
	public static int ERROR_FILE_TOO_BIG = -2;
	/** Constant indicate there is no more memory */
	public static int ERROR_OUT_OF_MEMORY = -3;
	/** Constant indicate there is no file is selected */
	public static int ERROR_NO_FILE_SELECTED = -4;

	/**
	 * Initialize database
	 */
	public static void initDB() {
		initialUserID = 1234;
		totalSize = 0;
		ServiceRegistry serviceRegistry;
		Configuration configuration = new Configuration();
		configuration.configure();
		serviceRegistry = new ServiceRegistryBuilder().applySettings(
				configuration.getProperties()).buildServiceRegistry();
		factory = configuration.buildSessionFactory(serviceRegistry);

	}

	/**
	 * Shutdown and clean up the database
	 */
	public static void cleanDB() {
		if (factory != null) {
			factory.close();
		}
		factory = null;
	}

	/**
	 * Save a given file to database
	 * 
	 * @param theFile
	 *            The file
	 * @return The ID of the file
	 */
	public static int saveFile(TheFile theFile) {
		if (theFile.getFileSize() > MAX_SIZE) {
			return ERROR_FILE_TOO_BIG;
		}
		if (theFile.getFileSize() + totalSize > MAX_CAP) {
			return ERROR_OUT_OF_MEMORY;
		}
		if (theFile.getFileName().compareTo("") == 0) {
			return ERROR_NO_FILE_SELECTED;
		}
		boolean notDone = true;
		while (notDone) {
			try {
				Session sec = createSession();
				sec.save(theFile);
				totalSize += theFile.getFileSize();
				closeSession(sec);
				notDone = false;
			} catch (Exception e) {
				notDone = true;
				e.printStackTrace();
			}
		}
		// Convert from database file ID to (user, normal) file ID to avoid
		// giving small ID (ie: 1,2,3)
		return theFile.getFileID() + initialUserID;
	}

	/**
	 * Create a new session
	 * 
	 * @return The new session
	 */
	private static Session createSession() {
		Session sec = factory.openSession();
		sec.beginTransaction();
		return sec;
	}

	/**
	 * Close a session
	 * 
	 * @param sec
	 *            The session need to be closed
	 */
	private static void closeSession(Session sec) {
		if (sec != null) {
			sec.getTransaction().commit();
			sec.close();
		}
	}

	/**
	 * Convert (user, normal) file ID to database file ID
	 * 
	 * @param fileID
	 *            The file ID
	 * @return The database file ID
	 */
	private static int processID(String fileID) {
		try {
			return Integer.parseInt(fileID) - initialUserID;
		} catch (Exception e) {
			// Not a number
			return -1;
		}
	}

	/**
	 * Check if there is any file in the database that has the given ID
	 * 
	 * @param fileID
	 *            The file ID
	 * @return True if and only if the database contains file with given ID
	 */
	public static boolean hasID(String fileID) {
		int fileCount = 0;
		Session sec = createSession();
		try {
			String sql = "select count(*) from TheFile where fileID = :fileID";
			Query query = sec.createQuery(sql);
			query.setParameter("fileID", processID(fileID));
			fileCount = Integer.parseInt(query.list().get(0).toString());
		} catch (Exception e) {
			closeSession(sec);
			return false;
		}
		closeSession(sec);
		return (fileCount > 0);
	}

	/**
	 * Get the name of the file with given file ID
	 * 
	 * @param fileID
	 *            The file ID
	 * @return The name of the file with given file ID, or empty string if there
	 *         is no file with such file ID
	 */
	public static String getFileName(String fileID) {
		String fileName;
		Session sec = createSession();
		try {
			String sql = "select fileName from TheFile where fileID = :fileID";
			Query query = sec.createQuery(sql);
			query.setParameter("fileID", processID(fileID));
			fileName = query.list().get(0).toString();
		} catch (Exception e) {
			closeSession(sec);
			return "";
		}
		closeSession(sec);
		return fileName;
	}

	/**
	 * Get the file object with the give file ID out of the database
	 * 
	 * @param fileID
	 *            The file ID
	 * @return The file object
	 */
	public static TheFile getFile(String fileID) {
		TheFile theFile;
		Session sec = createSession();
		try {
			String sql = "from TheFile where fileID = :fileID";
			Query query = sec.createQuery(sql);
			query.setParameter("fileID", processID(fileID));
			ArrayList<TheFile> result = new ArrayList<TheFile>();
			result.addAll(query.list());
			theFile = result.get(0);
		} catch (Exception e) {
			e.printStackTrace();
			closeSession(sec);
			return null;
		}
		closeSession(sec);
		return theFile;
	}

	/**
	 * Clean up all the old files in the database
	 */
	public static void cleanUpOldFiles() {
		if (factory == null) {
			return;
		}
		Session sec = createSession();
		try {
			Date fileTime;
			long timeDifferent;
			Date currentTime = new Date();
			String sql = "from TheFile";
			Query query = sec.createQuery(sql);
			ArrayList<TheFile> result = new ArrayList<TheFile>();
			result.addAll(query.list());
			for (TheFile file : result) {
				fileTime = file.getUploadTime();
				timeDifferent = currentTime.getTime() - fileTime.getTime();
				timeDifferent = timeDifferent / (60 * 60 * 1000 * 24);
				if (timeDifferent > 1) {
					totalSize -= file.getFileSize();
					sec.delete(file);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			closeSession(sec);
		}
		closeSession(sec);
	}
}
