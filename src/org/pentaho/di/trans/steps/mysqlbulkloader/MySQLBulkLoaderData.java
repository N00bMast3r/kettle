 /* Copyright (c) 2007 Pentaho Corporation.  All rights reserved. 
 * This software was developed by Pentaho Corporation and is provided under the terms 
 * of the GNU Lesser General Public License, Version 2.1. You may not use 
 * this file except in compliance with the license. If you need a copy of the license, 
 * please go to http://www.gnu.org/licenses/lgpl-2.1.txt. The Original Code is Pentaho 
 * Data Integration.  The Initial Developer is Pentaho Corporation.
 *
 * Software distributed under the GNU Lesser Public License is distributed on an "AS IS" 
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or  implied. Please refer to 
 * the license for the specific language governing your rights and limitations.*/

package org.pentaho.di.trans.steps.mysqlbulkloader;

import java.io.OutputStream;

import org.pentaho.di.core.database.Database;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.util.StreamLogger;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

/**
 * Stores data for the MySQL bulk load step.
 *
 * @author Matt
 * @since  14-apr-2009
 */
public class MySQLBulkLoaderData extends BaseStepData implements StepDataInterface
{
	public Database db;

	public int    keynrs[];         // nr of keylookup -value in row...
    
	public StreamLogger errorLogger;

	public StreamLogger outputLogger;

	public byte[] quote;
	public byte[] separator;
	public byte[] newline;

	public ValueMetaInterface bulkTimestampMeta;
	public ValueMetaInterface bulkDateMeta;
	public ValueMetaInterface bulkNumberMeta;
	
	public String schemaTable;

	public String fifoFilename;

	public OutputStream fifoStream;

	public MySQLBulkLoader.SqlRunner sqlRunner;

	public ValueMetaInterface[]	bulkFormatMeta;

	public long	bulkSize;
	
	/**
	 *  Default constructor.
	 */
	public MySQLBulkLoaderData()
	{
		super();

		db=null;
	}
}
