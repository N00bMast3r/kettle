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
 
package org.pentaho.di.trans.steps.insertupdate;

import java.sql.PreparedStatement;

import org.pentaho.di.core.database.Database;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;



/**
 * Stores data for the Insert/Update step.
 * 
 * @author Matt
 * @since 24-jan-2005
 */
public class InsertUpdateData extends BaseStepData implements StepDataInterface
{
	public Database db;

	public int    keynrs[];         // nr of keylookup -value in row...
	public int    keynrs2[];        // nr of keylookup2-value in row...
	public int    valuenrs[];       // Stream valuename nrs to prevent searches.

    public RowMetaInterface outputRowMeta;

    public String schemaTable;

    public PreparedStatement prepStatementLookup;
    public PreparedStatement prepStatementUpdate;
    
    public RowMetaInterface  updateParameterRowMeta;
    public RowMetaInterface  lookupParameterRowMeta;
    public RowMetaInterface  lookupReturnRowMeta;
    public RowMetaInterface  insertRowMeta;
	
	/**
	 *  Default constructor.
	 */
	public InsertUpdateData()
	{
		super();

		db=null;
	}
}