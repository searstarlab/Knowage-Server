/**

SpagoBI - The Business Intelligence Free Platform

Copyright (C) 2005-2010 Engineering Ingegneria Informatica S.p.A.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

**/
package it.eng.spagobi.tools.dataset.validation;

import it.eng.spagobi.rest.validation.IFieldsValidator;
import it.eng.spagobi.signup.validation.SignupFieldsValidator;

/**
 * @author Monica Franceschini (monica.franceschini@eng.it)
 *
 */
public class FieldsValidatorFactory {
	
	
	public static final String DATASET = "dataset";
	public static final String SIGNUP  = "signup";
	
	public IFieldsValidator getValidator(String type) {
		if ( type.equalsIgnoreCase(DATASET))
			return new DatasetFieldsValidator();
		else if ( type.equalsIgnoreCase(SIGNUP)) 
			return new SignupFieldsValidator();
		else	
			return null;
		
	}

}
