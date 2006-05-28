
package be.ibridge.kettle.core.database;

import be.ibridge.kettle.core.Const;
import be.ibridge.kettle.core.value.Value;

/**
 * Contains Hypersonic specific information through static final members 
 * 
 * @author Matt
 * @since  11-mrt-2005
 */
public class HypersonicDatabaseMeta extends BaseDatabaseMeta implements DatabaseInterface
{
	/**
	 * Construct a new database connection.
	 */
	public HypersonicDatabaseMeta(String name, String access, String host, String db, String port, String user, String pass)
	{
		super(name, access, host, db, port, user, pass);
	}
	
	public HypersonicDatabaseMeta()
	{
	}
	
	public String getDatabaseTypeDesc()
	{
		return "HYPERSONIC";
	}

	public String getDatabaseTypeDescLong()
	{
		return "Hypersonic";
	}
	
	/**
	 * @return Returns the databaseType.
	 */
	public int getDatabaseType()
	{
		return DatabaseMeta.TYPE_DATABASE_HYPERSONIC;
	}
		
	public int[] getAccessTypeList()
	{
		return new int[] { DatabaseMeta.TYPE_ACCESS_NATIVE, DatabaseMeta.TYPE_ACCESS_ODBC };
	}
	
	public int getDefaultDatabasePort()
	{
		if (getAccessType()==DatabaseMeta.TYPE_ACCESS_NATIVE) return 9001;
		return -1;
	}

	public String getDriverClass()
	{
		if (getAccessType()==DatabaseMeta.TYPE_ACCESS_ODBC)
		{
			return "sun.jdbc.odbc.JdbcOdbcDriver";
		}
		else
		{
			return "org.hsqldb.jdbcDriver";
		}
	}

	public String getURL()
	{
		if (getAccessType()==DatabaseMeta.TYPE_ACCESS_ODBC)
		{
			return "jdbc:odbc:"+getDatabaseName();
		}
		else
		{
			String port = getDatabasePortNumberString();
			if ( "0".equals(port) ) 
			{
				// When no port is specified, or port is 0 support local/memory
				// HSQLDB databases.
			    return "jdbc:hsqldb:"+getDatabaseName();
			}
			else
			{
			    return "jdbc:hsqldb:hsql://"+getHostname()+ ":" + port +"/"+getDatabaseName();
			}				
		}
	}
	
	/**
	 * @return true if the database supports bitmap indexes
	 */
	public boolean supportsBitmapIndex()
	{
		return false;
	}

	/**
	 * Generates the SQL statement to add a column to the specified table
	 * @param tablename The table to add
	 * @param v The column defined as a value
	 * @param tk the name of the technical key field
	 * @param use_autoinc whether or not this field uses auto increment
	 * @param pk the name of the primary key field
	 * @param semicolon whether or not to add a semi-colon behind the statement.
	 * @return the SQL statement to add a column to the specified table
	 * 
	 * TODO: Set to default: check if this is correct!
	 */
	public String getAddColumnStatement(String tablename, Value v, String tk, boolean use_autoinc, String pk, boolean semicolon)
	{
		return "ALTER TABLE "+tablename+" ADD "+getFieldDefinition(v, tk, pk, use_autoinc, true, false);
	}

	/**
	 * Generates the SQL statement to modify a column in the specified table
	 * @param tablename The table to add
	 * @param v The column defined as a value
	 * @param tk the name of the technical key field
	 * @param use_autoinc whether or not this field uses auto increment
	 * @param pk the name of the primary key field
	 * @param semicolon whether or not to add a semi-colon behind the statement.
	 * @return the SQL statement to modify a column in the specified table
	 */
	public String getModifyColumnStatement(String tablename, Value v, String tk, boolean use_autoinc, String pk, boolean semicolon)
	{
		return "ALTER TABLE "+tablename+" MODIFY "+getFieldDefinition(v, tk, pk, use_autoinc, true, false);
	}

	public String getFieldDefinition(Value v, String tk, String pk, boolean use_autoinc, boolean add_fieldname, boolean add_cr)
	{
		StringBuffer retval=new StringBuffer();
		
		String fieldname = v.getName();
		int    length    = v.getLength();
		int    precision = v.getPrecision();
		
		if (add_fieldname) retval.append(fieldname).append(" ");
		
		int type         = v.getType();
		switch(type)
		{
		case Value.VALUE_TYPE_DATE   : retval.append("TIMESTAMP"); break;
		case Value.VALUE_TYPE_BOOLEAN: retval.append("CHAR(1)"); break;
		case Value.VALUE_TYPE_NUMBER : 
		case Value.VALUE_TYPE_INTEGER: 
        case Value.VALUE_TYPE_BIGNUMBER: 
			if (fieldname.equalsIgnoreCase(tk) || // Technical key
			    fieldname.equalsIgnoreCase(pk)    // Primary key
			    ) 
			{
				retval.append("BIGSERIAL");
			} 
			else
			{
				if (length>0)
				{
					if (precision>0 || length>18)
					{
						retval.append("NUMERIC(").append(length).append(", ").append(precision).append(')');
					}
					else
					{
						if (length>9)
						{
							retval.append("BIGINT");
						}
						else
						{
							if (length<5)
							{
								retval.append("SMALLINT");
							}
							else
							{
								retval.append("INTEGER");
							}
						}
					}
					
				}
				else
				{
					retval.append("DOUBLE PRECISION");
				}
			}
			break;
		case Value.VALUE_TYPE_STRING:
			if (length>=DatabaseMeta.CLOB_LENGTH)
			{
				retval.append("TEXT");
			}
			else
			{
				retval.append("VARCHAR"); 
				if (length>0)
				{
					retval.append('(').append(length);
				}
				else
				{
					retval.append('('); // Maybe use some default DB String length?
				}
				retval.append(')');
			}
			break;
		default:
			retval.append(" UNKNOWN");
			break;
		}
		
		if (add_cr) retval.append(Const.CR);
		
		return retval.toString();
	}
}
