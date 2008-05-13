/*
 * $Id: Util.java,v 1.81 2007/08/19 15:55:31 jeffmc Exp $
 *
 * Copyright  1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */





package ucar.visad;


import ucar.unidata.geoloc.Bearing;
import ucar.unidata.geoloc.LatLonPointImpl;

import ucar.unidata.util.DatedObject;
import ucar.unidata.util.FileManager;

import ucar.unidata.util.Misc;
import ucar.unidata.util.TwoFacedObject;


import ucar.visad.data.*;

import ucar.visad.quantities.CommonUnits;
import ucar.visad.quantities.Length;

import visad.*;

import visad.browser.Convert;

import visad.data.netcdf.Plain;

import visad.data.units.NoSuchUnitException;

import visad.georef.*;

import visad.georef.EarthLocation;
import visad.georef.EarthLocation;
import visad.georef.EarthLocationTuple;
import visad.georef.LatLonPoint;

import visad.java2d.*;

import visad.java3d.*;

import visad.jmet.MetUnits;

import visad.util.*;

import visad.util.DataUtility;

import java.awt.GraphicsConfigTemplate;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;

import java.io.IOException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;



import java.rmi.RemoteException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.media.j3d.*;

import javax.vecmath.*;



/**
 * Provides support for utility functions.
 *
 * @author Unidata Development Team
 * @version $Revision: 1.81 $ $Date: 2007/08/19 15:55:31 $
 */
public final class Util {

    /** _more_ */
    private static final double TIMERANGE_DELTA = 0.00000001;

    /** The suffix added to text type names */
    public static final String TEXT_IDENTIFIER = "(Text)";


    /** The default root name for generic RealTypes */
    public static String REALTYPE_ROOT = "Util_RealType";

    /** type counter */
    private static int typeCnt = 0;

    /**
     * Default Constructor
     */
    private Util() {}

    /**
     * Gets the default units of the given range components of a FlatField.
     *
     * @param field             The field to be examined.
     * @param indexes           Indexes of the components in the range of the
     *                          field.
     * @param types             Expected, compatible types for the components.
     * @return                  The units of the range components.
     * @throws IllegalArgumentException
     *                          <code>field == null ||
     *                          indexes.length != types.length</code>,
     *                          or indexes out-of-bounds.
     * @throws TypeException    Type of component in range of field not
     *                          compatible with expected type.
     * @throws VisADException   Couldn't create necessary VisAD object.
     */
    protected static Unit[] getDefaultUnits(FlatField field, int[] indexes,
                                            RealType[] types)
            throws IllegalArgumentException, TypeException, VisADException {

        if (field == null) {
            throw new IllegalArgumentException("Null field");
        }

        if (indexes.length != types.length) {
            throw new IllegalArgumentException(
                "indexes.length != units.length");
        }

        RealTupleType rangeType =
            ((FunctionType) field.getType()).getFlatRange();
        int    rangeRank = rangeType.getDimension();
        Unit[] units     = new Unit[indexes.length];

        for (int i = 0; i < indexes.length; ++i) {
            int index = indexes[i];

            if ((index < 0) || (index >= rangeRank)) {
                throw new IllegalArgumentException("Index out-of-bounds");
            }

            RealType componentType = (RealType) rangeType.getComponent(index);

            if ( !componentType.equalsExceptNameButUnits(types[i])) {
                throw new TypeException(
                    "Actual type (" + componentType
                    + ") not compatible with expected type (" + types[i]
                    + ")");
            }

            units[i] = componentType.getDefaultUnit();
        }

        return units;
    }

    /**
     * Clones a RealType but changes the name.
     *
     * @param realType          The realtype to be cloned.
     * @param name              The new name for the clone.
     * @return                  The cloned realtype with the new name.
     * @throws VisADException   Couldn't create necessary VisAD object.
     */
    public static RealType clone(RealType realType, String name)
            throws VisADException {

        return RealType.getRealType(name, realType.getDefaultUnit(),
                                    realType.getDefaultSet(),
                                    realType.isInterval()
                                    ? RealType.INTERVAL
                                    : 0);
    }

    /**
     * Indicates if a {@link Data} object is compatible with a {@link MathType}.
     *
     * @param data              The {@link Data} object.
     * @param type              The {@link MathType}.
     * @return                  <code>true</code> if and only if the given
     *                          {@link Data} object is compatible with the given
     *                          {@link MathType}.
     * @throws VisADException   if a VisAD failure occurs.
     * @throws RemoteException  if a Java RMI failure occurs.
     */
    public static boolean isCompatible(Data data, MathType type)
            throws VisADException, RemoteException {

        MathType dataType = data.getType();

        if (dataType instanceof SetType) {
            dataType = ((SetType) dataType).getDomain();
        }

        if ((dataType instanceof FunctionType)
                && !(type instanceof FunctionType)) {
            dataType = ((FunctionType) dataType).getRange();
        }

        if (dataType instanceof TupleType) {
            dataType = DataUtility.simplify((TupleType) dataType);
        }

        if (type instanceof TupleType) {
            type = DataUtility.simplify((TupleType) type);
        }

        return type.equalsExceptNameButUnits(dataType);
    }

    /**
     * Vets a data object against a MathType.  The data object must be
     * compatible with the MathType or an exception is thrown.<p>
     *
     * This method should probably become MathType.vet(Data).<p>
     *
     * @param type              The MathType that the data object must be
     *                          compatible with.
     * @param data              The data object.
     * @return                  <code>data</code> (as a programming
     *                          convenience).
     * @throws TypeException    The MathType and data object are incompatible.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Data vetType(MathType type, Data data)
            throws TypeException, VisADException, RemoteException {

        if ( !isCompatible(data, type)) {
            throw new TypeException(
                "MathType of data object (" + data.getType()
                + ") is incompatible with required MathType (" + type + ')');
        }

        return data;
    }

    /**
     * Ensure that the units for the RealType are compatible with the given
     * unit.
     *
     * @param type  RealType to check
     * @param u  unit to check
     * @return the original RealType or a RealType with compatible units.
     * @throws VisADException couldn't get a new RealType
     */
    public static RealType ensureUnit(RealType type, Unit u)
            throws VisADException {

        if (Unit.canConvert(type.getDefaultUnit(), u)) {
            return type;
        }

        RealType newType = RealType.getRealType(type.getName() + "_"
                               + getCompatibleUnitName(u), u,
                                   type.getDefaultSet(),
                                   type.getAttributeMask());
        if (newType == null) {
            throw new VisADException(
                "couldn't create RealType with units compatible to "
                + u.toString());
        }
        return newType;
    }

    /**
     * Replace illegal RealType characters in a Unit spec with underscores.
     * @param u  Unit
     * @return a compatible unit name
     */
    public static String getCompatibleUnitName(Unit u) {
        return cleanName(u.toString());
    }

    /**
     * Make a valid VisAD RealType name from the string.  Remove
     * spaces, "." and parens.
     * @param name name to clean
     * @return cleaned up name
     */
    public static String cleanName(String name) {

        String newName = new String(name);

        if (newName.indexOf(".") > -1) {
            newName = newName.replace('.', '_');
        }
        if (newName.indexOf(" ") > -1) {
            newName = newName.replace(' ', '_');
        }
        if (newName.indexOf("(") > -1) {
            newName = newName.replace('(', '[');
        }
        if (newName.indexOf(")") > -1) {
            newName = newName.replace(')', ']');
        }
        return newName;
    }

    /**
     * Ensure that the units for the array of RealTypes are compatible
     * with the given unit.
     *
     * @param types array of RealTypes to check
     * @param u     unit to check
     * @return the original RealTypes or an array of RealTypes with
     *         compatible units.
     * @throws VisADException couldn't get a new RealType
     */
    public static RealType[] ensureUnit(RealType[] types, Unit u)
            throws VisADException {
        RealType[] newTypes = new RealType[types.length];
        for (int i = 0; i < types.length; i++) {
            newTypes[i] = ensureUnit(types[i], u);
        }
        return newTypes;
    }

    /**
     * Ensures that a data object has a particular MathType.  Clones the
     * data object only if necessary.<p>
     *
     * This method should probably become "abstract Data
     * Data.ensureMathType(MathType)" with subclass-dependent
     * implementations.<p>
     *
     * @param data              The data object.
     * @param type              The type for the returned data object.  Must be
     *                          compatible with the current type of the data
     *                          object.  The data object will be cloned only
     *                          if necessary.
     * @return                  A data object with the given MathType.
     * @throws UnimplementedException
     *                          Method not yet implemented.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Data ensureMathType(Data data, MathType type)
            throws UnimplementedException, VisADException, RemoteException {

        return data.getType().equals(type)
               ? data
               : clone(data, type);
    }

    /**
     * Ensures that a Field has a particular domain Set.  Clones the Field only
     * if necessary.
     *
     * @param field             The Field the have a particular domain Set.
     * @param domain            The particular domain Set for the Field to have.
     *                          Must be compatible with the Field's existing
     *                          domain set.
     * @throws UnimplementedException
     *                          Can't yet clone the class of the Field.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     * @return                  The Field with the particular domain set.
     */
    public static Field ensureDomain(Field field, Set domain)
            throws UnimplementedException, VisADException, RemoteException {

        Set oldDomain = field.getDomainSet();

        return oldDomain.equals(domain)
               ? field
               : clone(field, domain);
    }

    /**
     * Ensures that a Field has a particular domain MathType.  Clones the Field
     * only if necessary.
     *
     * @param field             The Field the have a particular domain Set.
     * @param newDomainType     The particular MathType for the domain of the
     *                          Field to have.  Coordinate conversions are
     *                          done if and when necessary and possible.
     * @param coordinateSystem  The CoordinateSystem which, together with
     *                          <code>newDomainType</code>, form the basis for
     *                          the desired domain.  May be <code>null</code>.
     * @throws UnimplementedException
     *                          Can't yet clone the class of the Field.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     * @return                  The Field with the particular domain set.
     */
    public static Field ensureDomain(Field field,
                                     RealTupleType newDomainType,
                                     CoordinateSystem coordinateSystem)
            throws UnimplementedException, VisADException, RemoteException {

        return !DataUtility.getDomainType(field).equals(newDomainType)
               ? convertDomain(field, newDomainType, coordinateSystem)
               : ((coordinateSystem == null)
                  || coordinateSystem.equals(
                      field.getDomainSet().getCoordinateSystem()))
                 ? field
                 : clone(field,
                         clone(field.getDomainSet(), coordinateSystem));
    }

    /**
     * Ensures that the range of a Field is a Tuple.
     *
     * @param field             The Field.
     * @return                  A Field identical to the input field but with
     *                          a Tuple range.  May be the same field.  The
     *                          range will be a RealTuple if possible.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Field ensureTupleRange(Field field)
            throws RemoteException, VisADException {
        return DataUtility.ensureRange(field,
                                       DataUtility.getRangeTupleType(field));
    }

    /**
     * Clones a Field replacing the domain Set and copying the range values.
     *
     * @param field             The Field to be cloned..
     * @param domain            The domain Set for the cloned Field to have.
     *                          Must be compatible with the Field's existing
     *                          domain set.
     * @return                  A clone of the Field with the particular domain
     *                          set.
     * @throws UnimplementedException
     *                          Can't yet clone the class of the Field.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Field clone(Field field, Set domain)
            throws UnimplementedException, VisADException, RemoteException {
        return clone(field, domain, true);
    }

    /**
     * Clones a Field replacing the domain Set.
     *
     * @param field             The Field to be cloned.
     * @param domain            The domain Set for the cloned Field to have.
     *                          Need only be topologically equivalent to the
     *                          Field's existing domain set.
     * @param copyRange         If <code>true</code> then the range of the old
     *                          Field will be copied to the new Field;
     *                          otherwise not.
     * @return                  A clone of the Field with the particular domain
     *                          set.
     * @throws UnimplementedException
     *                          Can't yet clone the class of the Field.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Field clone(Field field, Set domain, boolean copyRange)
            throws UnimplementedException, VisADException, RemoteException {

        if ( !(field instanceof FieldImpl)) {
            throw new UnimplementedException("Can't yet clone "
                                             + field.getClass());
        }

        Field newField;

        if (field instanceof FlatField) {
            newField = clone((FlatField) field, domain, copyRange);
        } else {
            FieldImpl    fieldImpl = (FieldImpl) field;
            FunctionType oldFuncType = (FunctionType) fieldImpl.getType();
            RealTupleType newDomainTupleType =
                ((SetType) domain.getType()).getDomain();
            FunctionType newFuncType =
                newDomainTupleType.equalsExceptNameButUnits(
                    oldFuncType.getDomain())
                ? oldFuncType
                : new FunctionType(newDomainTupleType,
                                   oldFuncType.getRange());

            newField = new FieldImpl(newFuncType, domain);

            if (copyRange && !fieldImpl.isMissing()) {
                for (int i = fieldImpl.getLength(); --i >= 0; ) {
                    ((FieldImpl) newField).setSample(i,
                            fieldImpl.getSample(i), false);
                }
            }
        }

        return newField;
    }

    /**
     * Clones a FlatField replacing the domain Set.  The actual range units
     * in the clone may differ from those of the input FlatField.
     *
     * @param flatField         The FlatField to be cloned.
     * @param domain            The domain Set for the cloned FlatField to have.
     *                          Need only be topologically equivalent to
     *                          existing domain.
     * @param copyRange         If <code>true</code> then the range of the old
     *                          FlatField will be copied to the new Field;
     *                          otherwise not.
     * @return                  A clone of the FlatField with the particular
     *                          domain set.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static FlatField clone(FlatField flatField, Set domain,
                                  boolean copyRange)
            throws VisADException, RemoteException {

        CoordinateSystem rangeCoordinateSystem =
            getRangeCoordinateSystem(flatField);
        CoordinateSystem[] rangeCoordinateSystems =
            getRangeCoordinateSystems(flatField);
        Set[]        rangeSets   = flatField.getRangeSets();
        Unit[]       rangeUnits  = getRangeUnits(flatField);
        FunctionType oldFuncType = (FunctionType) flatField.getType();
        RealTupleType newDomainTupleType =
            ((SetType) domain.getType()).getDomain();
        FunctionType newFuncType =
            newDomainTupleType.equalsExceptNameButUnits(
                oldFuncType.getDomain())
            ? oldFuncType
            : new FunctionType(newDomainTupleType, oldFuncType.getRange());
        FlatField newField = new FlatField(newFuncType, domain,
                                           rangeCoordinateSystem,
                                           rangeCoordinateSystems,
                                           flatField.getRangeSets(),
                                           (Unit[]) null);  // use default units

        if (copyRange && !flatField.isMissing()) {
            ((FlatField) newField).setSamples(flatField.getValues(true),
                    false);  // default units
        }

        return newField;
    }

    /**
     * Converts the MathType of the domain of a Field.
     *
     * @param field             The Field to be converted.
     * @param newDomainType     The MathType of the domain for the converted
     *                          Field to have.  Coordinate conversions are
     *                          done if and when necessary and possible.
     * @param coordinateSystem  The CoordinateSystem which, together with
     *                          <code>newDomainType</code>, form the basis for
     *                          the desired domain.  May be <code>null</code>.
     * @return                  A clone of the Field with the particular domain
     *                          type.
     * @throws UnimplementedException
     *                          Can't yet clone the class of the Field.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Field convertDomain(Field field,
                                      RealTupleType newDomainType,
                                      CoordinateSystem coordinateSystem)
            throws UnimplementedException, VisADException, RemoteException {

        if ( !(field instanceof FieldImpl)) {
            throw new UnimplementedException("Can't yet clone "
                                             + field.getClass());
        }

        Field newField;

        if (field instanceof FlatField) {
            newField = convertDomain((FlatField) field, newDomainType,
                                     coordinateSystem);
        } else {
            FieldImpl fieldImpl = (FieldImpl) field;

            newField =
                new FieldImpl(
                    new FunctionType(
                        newDomainType,
                        DataUtility.getRangeType(field)), convertDomain(
                            (SampledSet) field.getDomainSet(), newDomainType,
                            coordinateSystem));

            if ( !fieldImpl.isMissing()) {
                for (int i = fieldImpl.getLength(); --i >= 0; ) {
                    ((FieldImpl) newField).setSample(i,
                            fieldImpl.getSample(i), false);
                }
            }
        }

        return newField;
    }

    /**
     * Converts the domain of a FlatField.
     *
     * @param field             The FlatField to be converted.
     * @param newDomainType     The MathType for the converted FlatField.
     *                          Coordinate conversions are done if and when
     *                          necessary and possible.
     * @param coordinateSystem  The CoordinateSystem which, together with
     *                          <code>newDomainType</code>, form the basis for
     *                          the desired domain.  May be <code>null</code>.
     * @return                  A copy of the input Field but with the domain
     *                          converted to the new MathType.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static FlatField convertDomain(FlatField field,
                                          RealTupleType newDomainType,
                                          CoordinateSystem coordinateSystem)
            throws VisADException, RemoteException {

        CoordinateSystem rangeCoordinateSystem =
            getRangeCoordinateSystem(field);
        CoordinateSystem[] rangeCoordinateSystems =
            getRangeCoordinateSystems(field);
        Set[]      rangeSets  = field.getRangeSets();
        Unit[]     rangeUnits = getRangeUnits(field);
        SampledSet oldDomain  = (SampledSet) field.getDomainSet();
        RealTupleType oldDomainType =
            ((SetType) oldDomain.getType()).getDomain();
        ErrorEstimate[] oldErrors = oldDomain.getSetErrors();
        ErrorEstimate[] newErrors = new ErrorEstimate[oldErrors.length];
        Unit[]          newUnits  = (coordinateSystem == null)
                                    ? newDomainType.getDefaultUnits()
                                    : coordinateSystem
                                        .getCoordinateSystemUnits();
        double[][] newDomainValues =
            CoordinateSystem.transformCoordinates(newDomainType,
                coordinateSystem, newUnits, newErrors, oldDomainType,
                oldDomain.getCoordinateSystem(), oldDomain.getSetUnits(),
                oldErrors, oldDomain.getDoubles());
        FlatField newField =
            new FlatField(
                new FunctionType(
                    newDomainType,
                    DataUtility.getRangeType(field)), newSampledSet(
                        oldDomain, newDomainType, newDomainValues,
                        coordinateSystem, newUnits,
                        newErrors), rangeCoordinateSystem,
                                    rangeCoordinateSystems,
                                    field.getRangeSets(), (Unit[]) null);  // default units

        if ( !field.isMissing()) {
            newField.setSamples(field.getValues(true), false);
        }

        // default units
        return newField;
    }

    /**
     * Converts a SampledSet domain.
     *
     * @param oldDomain         The domain to be converted.
     * @param newDomainType     The type of the new domain.
     * @param coordinateSystem  The CoordinateSystem which, together with
     *                          <code>newDomainType</code>, form the basis for
     *                          the desired domain.  May be <code>null</code>.
     * @return                  The old domain converted into the new type.
     *                          Coordinate system transformations are done if
     *                          and when necessary and possible.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet convertDomain(SampledSet oldDomain,
                                           RealTupleType newDomainType,
                                           CoordinateSystem coordinateSystem)
            throws VisADException, RemoteException {

        RealTupleType oldDomainType =
            ((SetType) oldDomain.getType()).getDomain();
        ErrorEstimate[] oldErrors = oldDomain.getSetErrors();
        ErrorEstimate[] newErrors = new ErrorEstimate[oldErrors.length];
        Unit[]          newUnits  = (coordinateSystem == null)
                                    ? newDomainType.getDefaultUnits()
                                    : coordinateSystem
                                        .getCoordinateSystemUnits();
        double[][] newDomainValues =
            CoordinateSystem.transformCoordinates(newDomainType,
                coordinateSystem, newUnits, newErrors, oldDomainType,
                oldDomain.getCoordinateSystem(), oldDomain.getSetUnits(),
                oldErrors, oldDomain.getDoubles());

        return newSampledSet(oldDomain, newDomainType, newDomainValues,
                             coordinateSystem, newUnits, newErrors);
    }

    /**
     * Creates a new SampledSet based on an existing SampledSet and new values.
     *
     * @param oldSampledSet     The existing SampledSet.
     * @param newValues         The values for the new SampledSet.
     * @param manifoldLengths   The manifold dimensionality (i.e. the
     *                          number and size of each dimension).  May be
     *                          <code>null</code> in which case an IrregularSet
     *                          is created and returned; otherwise, the first
     *                          element is the size of the innermost dimension,
     *                          etc.
     * @return                  The new SampledSet corresponding to the input
     *                          arguments.
     * @throws VisADException   Couldn't perform necessary VisAD operation.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet newSampledSet(SampledSet oldSampledSet,
                                           float[][] newValues,
                                           int[] manifoldLengths)
            throws VisADException, RemoteException {

        return newSampledSet(manifoldLengths,
                             ((SetType) oldSampledSet.getType()).getDomain(),
                             newValues, oldSampledSet.getCoordinateSystem(),
                             oldSampledSet.getSetUnits(),
                             oldSampledSet.getSetErrors());
    }

    /**
     * Creates a new SampledSet based on an existing SampledSet and new values.
     *
     * @param oldSampledSet     The existing SampledSet.
     * @param newValues         The values for the new SampledSet.
     * @param manifoldLengths   The manifold dimensionality (i.e. the
     *                          number and size of each dimension).  May be
     *                          <code>null</code> in which case an IrregularSet
     *                          is created and returned; otherwise, the first
     *                          element is the size of the innermost dimension,
     *                          etc.
     * @return                  The new SampledSet corresponding to the input
     *                          arguments.
     * @throws VisADException   Couldn't perform necessary VisAD operation.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet newSampledSet(SampledSet oldSampledSet,
                                           double[][] newValues,
                                           int[] manifoldLengths)
            throws VisADException, RemoteException {

        return newSampledSet(manifoldLengths,
                             ((SetType) oldSampledSet.getType()).getDomain(),
                             newValues, oldSampledSet.getCoordinateSystem(),
                             oldSampledSet.getSetUnits(),
                             oldSampledSet.getSetErrors());
    }

    /**
     * Creates a new SampledSet using the topology of an existing SampledSet.
     *
     * @param oldSampledSet     The old SampledSet to be used for the topology
     *                          of the new SampledSet.
     * @param newSampledSetType The type of the new SampledSet.
     * @param newValues         The values for the new SampledSet.
     * @param coordinateSystem  The CoordinateSystem for the new SampledSet.
     * @param newUnits          The units for the new SampledSet.
     * @param newErrors         The new ErrorEstimate-s for the new SampledSet.
     * @return                  The new SampledSet corresponding to the input
     *                          arguments.
     * @throws VisADException   Couldn't perform necessary VisAD operation.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet newSampledSet(SampledSet oldSampledSet,
                                           RealTupleType newSampledSetType,
                                           float[][] newValues,
                                           CoordinateSystem coordinateSystem,
                                           Unit[] newUnits,
                                           ErrorEstimate[] newErrors)
            throws VisADException, RemoteException {

        int[] manifoldLenths = (oldSampledSet instanceof GriddedSet)
                               ? ((GriddedSet) oldSampledSet).getLengths()
                               : (oldSampledSet instanceof SingletonSet)
                                 ? new int[] { 1 }
                                 : null;

        return newSampledSet(manifoldLenths, newSampledSetType, newValues,
                             coordinateSystem, newUnits, newErrors);
    }

    /**
     * Creates a new SampledSet using the topology of an existing SampledSet.
     *
     * @param oldSampledSet     The old SampledSet to be used for the topology
     *                          of the new SampledSet.
     * @param newSampledSetType The type of the new SampledSet.
     * @param newValues         The values for the new SampledSet.
     * @param coordinateSystem  The CoordinateSystem for the new SampledSet.
     * @param newUnits          The units for the new SampledSet.
     * @param newErrors         The new ErrorEstimate-s for the new SampledSet.
     * @return                  The new SampledSet corresponding to the input
     *                          arguments.
     * @throws VisADException   Couldn't perform necessary VisAD operation.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet newSampledSet(SampledSet oldSampledSet,
                                           RealTupleType newSampledSetType,
                                           double[][] newValues,
                                           CoordinateSystem coordinateSystem,
                                           Unit[] newUnits,
                                           ErrorEstimate[] newErrors)
            throws VisADException, RemoteException {

        int[] manifoldLenths = (oldSampledSet instanceof GriddedSet)
                               ? ((GriddedSet) oldSampledSet).getLengths()
                               : (oldSampledSet instanceof SingletonSet)
                                 ? new int[] { 1 }
                                 : null;

        return newSampledSet(manifoldLenths, newSampledSetType, newValues,
                             coordinateSystem, newUnits, newErrors);
    }

    /**
     * Creates a new SampledSet using a given manifold dimensionality.
     *
     * @param manifoldLengths   The manifold dimensionality (i.e. the
     *                          number and size of each dimension).  May be
     *                          <code>null</code> in which case an IrregularSet
     *                          is created and returned; otherwise, the first
     *                          element is the size of the innermost dimension,
     *                          etc.
     * @param newSampledSetType The type of the new SampledSet.
     * @param newValues         The values for the new SampledSet.
     * @param coordinateSystem  The CoordinateSystem for the new SampledSet.
     * @param newUnits          The units for the new SampledSet.
     * @param newErrors         The new ErrorEstimate-s for the new SampledSet.
     * @return                  The new SampledSet corresponding to the input
     *                          arguments.  Will be an IrregularSet, a
     *                          GriddedSet, or a SingletonSet.
     * @throws VisADException   Couldn't perform necessary VisAD operation.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet newSampledSet(int[] manifoldLengths,
                                           RealTupleType newSampledSetType,
                                           double[][] newValues,
                                           CoordinateSystem coordinateSystem,
                                           Unit[] newUnits,
                                           ErrorEstimate[] newErrors)
            throws VisADException, RemoteException {

        return newSampledSet(manifoldLengths, newSampledSetType,
                             Set.doubleToFloat(newValues), coordinateSystem,
                             newUnits, newErrors);
    }

    /**
     * Creates a new SampledSet using a given manifold dimensionality.
     *
     * @param manifoldLengths   The manifold dimensionality (i.e. the
     *                          number and size of each dimension).  May be
     *                          <code>null</code> in which case an IrregularSet
     *                          is created and returned; otherwise, the first
     *                          element is the size of the innermost dimension,
     *                          etc.
     * @param newSampledSetType The type of the new SampledSet.
     * @param newValues         The values for the new SampledSet.
     * @param coordinateSystem  The CoordinateSystem for the new SampledSet.
     * @param newUnits          The units for the new SampledSet.
     * @param newErrors         The new ErrorEstimate-s for the new SampledSet.
     * @return                  The new SampledSet corresponding to the input
     *                          arguments.  Will be an IrregularSet, a
     *                          GriddedSet, or a SingletonSet.
     * @throws VisADException   Couldn't perform necessary VisAD operation.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet newSampledSet(int[] manifoldLengths,
                                           RealTupleType newSampledSetType,
                                           float[][] newValues,
                                           CoordinateSystem coordinateSystem,
                                           Unit[] newUnits,
                                           ErrorEstimate[] newErrors)
            throws VisADException, RemoteException {

        SampledSet newSampledSet;

        if (manifoldLengths != null) {
            int product = 1;

            for (int i = manifoldLengths.length; --i >= 0; ) {
                product *= manifoldLengths[i];
            }

            if (product != 1) {
                newSampledSet = GriddedSet.create(newSampledSetType,
                        newValues, manifoldLengths, coordinateSystem,
                        newUnits, newErrors);
            } else {
                Real[] reals = new Real[newSampledSetType.getDimension()];

                for (int i = reals.length; --i >= 0; ) {
                    reals[i] = new Real(
                        (RealType) newSampledSetType.getComponent(i),
                        newValues[i][0], newUnits[i], newErrors[i]);
                }

                newSampledSet =
                    new SingletonSet(new RealTuple(newSampledSetType, reals,
                        coordinateSystem), coordinateSystem, newUnits,
                                           newErrors);
            }
        } else {

            /*
             * Set must be irregular.
             */
            switch (newSampledSetType.getDimension()) {

              case 1 :
                  newSampledSet = new Irregular1DSet(newSampledSetType,
                          newValues, coordinateSystem, newUnits, newErrors);
                  break;

              case 2 :
                  newSampledSet = new Irregular2DSet(newSampledSetType,
                          newValues, coordinateSystem, newUnits, newErrors,
                          null);
                  break;

              case 3 :
                  newSampledSet = new Irregular3DSet(newSampledSetType,
                          newValues, coordinateSystem, newUnits, newErrors,
                          null);
                  break;

              default :
                  newSampledSet = new IrregularSet(newSampledSetType,
                          newValues, coordinateSystem, newUnits, newErrors,
                          null);
            }
        }

        return newSampledSet;
    }

    /**
     * Gets the coordinate sytem of the RealTuple range of a Field.
     *
     * @param field             The Field.
     * @return                  The CoordinateSystem of the range of the Field
     *                          if appropriate; otherwise <code>null</code>.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static CoordinateSystem getRangeCoordinateSystem(Field field)
            throws VisADException, RemoteException {

        return ((FunctionType) field.getType()).getReal()
               ? field.getRangeCoordinateSystem()[0]
               : null;
    }

    /**
     * Gets the coordinate sytems of the components of the range of a Field.
     *
     * @param field             The Field.
     * @return                  The CoordinateSystems of the components of the
     *                          range of the Field if appropriate; otherwise,
     *                          <code>null</code>.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static CoordinateSystem[] getRangeCoordinateSystems(Field field)
            throws VisADException, RemoteException {

        CoordinateSystem[] coordSyses;
        FunctionType       funcType = (FunctionType) field.getType();

        if (funcType.getReal()) {
            coordSyses = null;
        } else {
            int componentCount =
                ((TupleType) funcType.getRange()).getDimension();

            coordSyses = new CoordinateSystem[componentCount];

            for (int i = componentCount; --i >= 0; ) {
                coordSyses[i] = field.getRangeCoordinateSystem(i)[0];
            }
        }

        return coordSyses;
    }

    /**
     * Gets the units of the (flat) components of the range of a FlatField.
     *
     * @param field             The FlatField.
     * @return                  The units of the (flat) components of the
     *                          range of the FlatField.  Won't be
     *                          <code>null</code>.
     */
    public static Unit[] getRangeUnits(FlatField field) {

        Unit[][] units  = field.getRangeUnits();
        Unit[]   result = new Unit[units.length];

        for (int i = result.length; --i >= 0; ) {
            result[i] = units[i][0];
        }

        return result;
    }


    /**
     * Gets the default units of the (flat) components of the range of a FlatField.
     *
     * @param field             The FlatField.
     * @return                  The default units of the (flat) components of the
     *                          range of the FlatField.  Won't be
     *                          <code>null</code>.
     */
    public static Unit[] getDefaultRangeUnits(FieldImpl field) {
        return ((FunctionType) field.getType()).getFlatRange()
            .getDefaultUnits();
    }

    /**
     * Clones a data object, replacing the MathType.  If the input data
     * object is a FlatField, then the actual range units of the clone may
     * differ from those of the input data object.<p>
     *
     * This method should probably become "abstract Data Data.clone(MathType)"
     * with subclass-dependent implementations.<p>
     *
     * @param data              The data object to be cloned.
     * @param type              The type for the returned data object.  Must be
     *                          compatible with the current type of the data
     *                          object.
     * @return                  The cloned data object with the new MathType.
     * @throws UnimplementedException
     *                          Method not yet implemented.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Data clone(Data data, MathType type)
            throws UnimplementedException, VisADException, RemoteException {
        return clone(data, type, false, true);
    }

    /**
     * Clones a data object, replacing the MathType.  If the input data
     * object is a FlatField, then the actual range units of the clone may
     * differ from those of the input data object.<p>
     *
     * This method should probably become "abstract Data Data.clone(MathType)"
     * with subclass-dependent implementations.<p>
     *
     * @param data              The data object to be cloned.
     * @param type              The type for the returned data object.  Must be
     *                          compatible with the current type of the data
     *                          object.
     * @param unitOverride      if the units are not compatible, if true,
     *                          assume values are in units of new type.
     * @param copy              if true, copy the data values for Fields.
     * @return                  The cloned data object with the new MathType.
     * @throws UnimplementedException
     *                          Method not yet implemented.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Data clone(Data data, MathType type, boolean unitOverride,
                             boolean copy)
            throws UnimplementedException, VisADException, RemoteException {

        Data result;


        if (data instanceof Field) {
            Field field = (Field) data;

            if ( !(type instanceof FunctionType)) {

                /*
                 * Assume that the given MathType is the desired type for the
                 * range of the Field.
                 */
                type = new FunctionType(
                    ((FunctionType) field.getType()).getDomain(), type);
            }

            FunctionType funcType = (FunctionType) type;

            if (data instanceof FlatField) {
                FlatField oldFlatField = (FlatField) data;
                //System.out.println("ff = " + oldFlatField.getType());
                //System.out.println("new type = " + funcType);
                // if copy == true, new set will be created by ensureMathType
                // because funcType.getDomain() is not a SetType
                Set newDomain =
                    (Set) ensureMathType(oldFlatField.getDomainSet(), (copy)
                        ? (MathType) funcType.getDomain()
                        : (MathType) new SetType(funcType.getDomain()));
                Set[] oldRangeSets = oldFlatField.getRangeSets();
                Unit[] oldUnits    = oldFlatField.getDefaultRangeUnits();
                Unit[] newUnits = funcType.getFlatRange().getDefaultUnits();
                CoordinateSystem oldCS =
                    getRangeCoordinateSystem(oldFlatField);
                CoordinateSystem[] oldRangeCS =
                    getRangeCoordinateSystems(oldFlatField);
                Set[] newRangeSets = oldRangeSets;

                if (unitOverride
                        && !Unit.canConvertArray(oldUnits, newUnits)) {
                    oldUnits = newUnits;
                }
                if ( !funcType.getRange().equalsExceptNameButUnits(
                        ((FunctionType) oldFlatField.getType()).getRange())) {
                    /*
                      System.out.println(funcType.getRange() + "!=" +
                      ((FunctionType)oldFlatField.getType()).getRange());
                    */
                    oldCS      = null;
                    oldRangeCS = null;
                    RealTupleType flatRange = funcType.getFlatRange();
                    newRangeSets = new Set[oldRangeSets.length];
                    for (int i = 0; i < flatRange.getDimension(); i++) {
                        newRangeSets[i] = ((RealType) flatRange.getComponent(
                            i)).getDefaultSet();
                        if (newRangeSets[i] == null) {
                            newRangeSets[i] = (oldRangeSets[i]
                                    instanceof DoubleSet)
                                    ? (Set) new DoubleSet(
                                        new SetType(
                                            flatRange.getComponent(i)))
                                    : (Set) new FloatSet(
                                        new SetType(
                                            flatRange.getComponent(i)));
                        }
                    }
                }
                FlatField newFlatField;
                if (oldFlatField instanceof CachedFlatField) {
                    newFlatField = ((CachedFlatField) oldFlatField).cloneMe
                    /*new CachedFlatField((CachedFlatField) oldFlatField,*/
                    (copy, funcType, newDomain, oldCS, oldRangeCS,
                     newRangeSets, oldUnits);
                } else {
                    newFlatField = new FlatField(funcType, newDomain, oldCS,
                            oldRangeCS,
                    //getRangeCoordinateSystem(oldFlatField),
                    //getRangeCoordinateSystems(oldFlatField),
                    newRangeSets, oldUnits);

                    if ( !oldFlatField.isMissing()) {
                        boolean useFloats = true;
                        for (int i = oldRangeSets.length; --i >= 0; ) {
                            if (oldRangeSets[i] instanceof DoubleSet) {
                                useFloats = false;
                                break;
                            }
                        }
                        if (useFloats) {
                            newFlatField.setSamples(
                                oldFlatField.getFloats(copy), false);
                            //default units
                        } else {
                            newFlatField.setSamples(
                                oldFlatField.getValues(copy), false);
                        }
                    }
                }
                result = newFlatField;
            } else {
                result = data.changeMathType(funcType);
            }
        } else if (data instanceof Set) {
            result = (Set) ((Set) data).cloneButType(type);
        } else if (data instanceof Real) {
            Real real = (Real) data;

            if (type instanceof RealType) {
                result = new Real((RealType) type, real.getValue(),
                                  real.getUnit(), real.getError());
            } else if (type instanceof RealTupleType) {
                RealTupleType realTupleType = (RealTupleType) type;

                result =
                    new RealTuple((RealTupleType) type,
                                  new Real[] {
                                      (Real) Util.clone(real,
                                          realTupleType.getComponent(0),
                                          unitOverride,
                                          copy) }, (CoordinateSystem) null);
            } else {
                throw new UnimplementedException(
                    "Util.clone(Data,MathType): "
                    + "Can't yet convert Real into type \"" + type + "\"");
            }
        } else if (data instanceof Tuple) {
            Tuple tuple = (Tuple) data;

            if (type instanceof RealType) {
                RealType realType = (RealType) type;

                if (tuple.getDimension() != 1) {
                    throw new TypeException(
                        "Util.clone(Data,MathType): "
                        + "Can't convert multi-dimensional Tuple into Real");
                } else {
                    result = clone(tuple.getComponent(0), realType,
                                   unitOverride, copy);
                }
            } else if (type instanceof TupleType) {
                TupleType tupleType = (TupleType) type;
                int       rank      = tuple.getDimension();

                if (rank != tupleType.getDimension()) {
                    throw new TypeException("Util.clone(Data,MathType): "
                                            + "Can't convert rank " + rank
                                            + " Tuple into rank "
                                            + tupleType.getDimension()
                                            + " Tuple");
                } else {
                    if (tupleType instanceof RealTupleType) {
                        RealTupleType realTupleType =
                            (RealTupleType) tupleType;
                        Real[] reals = new Real[rank];

                        for (int i = 0; i < rank; ++i) {
                            reals[i] = (Real) clone(tuple.getComponent(i),
                                    (RealType) realTupleType.getComponent(i),
                                    unitOverride, copy);
                        }

                        result = new RealTuple(realTupleType, reals,
                                (tuple instanceof RealTuple)
                                ? ((RealTuple) tuple).getCoordinateSystem()
                                : (CoordinateSystem) null);
                    } else {
                        Data[] datums = new Data[rank];

                        for (int i = 0; i < rank; ++i) {
                            datums[i] = clone(tuple.getComponent(i),
                                    tupleType.getComponent(i), unitOverride,
                                    copy);
                        }

                        result = new Tuple(tupleType, datums, false);
                    }
                }
            } else {
                throw new UnimplementedException(
                    "Util.clone(Data,MathType): "
                    + "Can't yet convert Tuple into type \"" + type + "\"");
            }
        } else {
            throw new UnimplementedException(
                "Cloning " + data.getClass()
                + " data object with change of MathType not yet supported");
        }

        return result;
    }

    /**
     * Ensures that a set has a particular CoordinateSystem.  Clones the
     * set only if necessary<p>
     *
     * This method should probably become "abstract Set
     * Set.ensureCoordinateSystem(CoordinateSystem)".<p>
     *
     * @param set               The set.
     * @param coordSys          The CoordinateSystem for the returned Set.  May
     *                          be <code>null</code>.
     * @return                  A set like <code>set</code> with the given
     *                          coordinate system.  Clones the set only if
     *                          necessary.
     * @throws UnimplementedException
     *                          Can't clone set.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Set ensureCoordinateSystem(Set set,
                                             CoordinateSystem coordSys)
            throws UnimplementedException, VisADException, RemoteException {

        CoordinateSystem origCoordSys = set.getCoordinateSystem();

        return ((coordSys == null) && (origCoordSys == null))
               || ((coordSys != null) && coordSys.equals(origCoordSys))
               ? set
               : clone(set, coordSys);
    }

    /**
     * Clones a set, replacing the CoordinateSystem.</p>
     *
     * <p>This method should probably become "abstract Set
     * Set.clone(CoordinateSystem)".
     *
     * @param set               The set to be cloned.
     * @param coordSys          The CoordinateSystem for the returned Set.  May
     *                          be <code>null</code>.
     * @return                  A clone of <code>set</code> but with the given
     *                          CoordinateSystem.
     * @throws UnimplementedException
     *                          Method not yet implemented for particular set.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static Set clone(Set set, CoordinateSystem coordSys)
            throws UnimplementedException, VisADException, RemoteException {

        Set clone;

        if (set instanceof IntegerSet) {
            GriddedSet griddedSet = (GriddedSet) set;

            clone = IntegerNDSet.create(set.getType(),
                                        griddedSet.getLengths(), coordSys,
                                        set.getSetUnits(),
                                        set.getSetErrors());
        } else if (set instanceof LinearSet) {
            GriddedSet griddedSet = (GriddedSet) set;
            int        rank       = griddedSet.getDimension();
            double[]   firsts     = new double[rank];
            double[]   lasts      = new double[rank];
            LinearSet  linearSet  = (LinearSet) set;

            for (int i = 0; i < rank; ++i) {
                Linear1DSet linear1DSet = linearSet.getLinear1DComponent(i);

                firsts[i] = linear1DSet.getFirst();
                lasts[i]  = linear1DSet.getLast();
            }

            clone = (Set) LinearNDSet.create(set.getType(), firsts, lasts,
                                             griddedSet.getLengths(),
                                             coordSys, set.getSetUnits(),
                                             set.getSetErrors());
        } else if (set instanceof SampledSet) {
            clone = newSampledSet((SampledSet) set,
                                  ((SetType) set.getType()).getDomain(),
                                  set.getDoubles(true), coordSys,
                                  set.getSetUnits(), set.getSetErrors());
        } else {
            throw new UnimplementedException(
                "Util.clone(Set,CoordinateSystem): " + "Can't clone class "
                + set.getClass());
        }

        return clone;
    }

    /**
     * Extracts the given range component from a Field as a SampledSet.
     * @param field             The field to have a component extracted.
     * @param type              The MathType of the component to be
     *                          extracted.
     * @return                  The SampledSet of component values.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet getRangeSampledSet(Field field, MathType type)
            throws VisADException, RemoteException {

        Field componentField = DataUtility.ensureRange(field, type);

        return newSampledSet(type, componentField.getFloats( /*copy=*/true),  // default units
                             (int[]) null,
                             componentField.getRangeCoordinateSystem()[0],
                             componentField.getDefaultRangeUnits(), (  // default units
                                 componentField instanceof FlatField)
                ? ((FlatField) componentField).getRangeErrors()
                : (ErrorEstimate[]) null,
        /*copy=*/
        false);
    }

    /**
     * Ensures that the MathType of the range of a FlatField is a specified
     * RealType.  Clones the FlatField only if necessary.
     *
     * @param flatField         The FlatField to have it's range be
     *                          <code>type</code>.
     * @param realType          The type for the returned FlatField.
     * @return                  A FlatField identical to
     *                          <code>flatFieldield</code> with
     *                          <code>realType</code> as the MathType of the
     *                          range.  May be <code>flatField</code>.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static FlatField ensureFlatFieldRangeType(FlatField flatField,
            RealType realType)
            throws VisADException, RemoteException {

        if ( !DataUtility.getRangeType(flatField).equals(realType)) {
            FlatField newFlatField =
                new FlatField(
                    new FunctionType(
                        DataUtility.getDomainType(flatField),
                        realType), flatField.getDomainSet(),
                                   getRangeCoordinateSystem(flatField),
                                   getRangeCoordinateSystems(flatField),
                                   flatField.getRangeSets(),
                                   flatField.getDefaultRangeUnits());  // use default units

            newFlatField.setSamples(flatField.getValues(true), false);  // default units
            newFlatField.setRangeErrors(flatField.getRangeErrors());

            flatField = newFlatField;
        }

        return flatField;
    }

    /**
     * Creates a new SampledSet.
     * @param type              The type for the SampledSet.
     * @param values            The values for the SampledSet.
     * @param manifoldLengths   The dimensionality of the manifold with the
     *                          first element corresponding to the innermost
     *                          manifold dimension.  Ignored if the values
     *                          constitute a single data point.  May be
     *                          <code>null</code>, in which case the manifold
     *                          is assumed to be 1-dimensional and the
     *                          resulting set will be an IrregularSet.  If non-
     *                          <code>null</code>, then the resulting set will
     *                          be a GriddedSet.
     * @param coordinateSystem  The coordinate system transformation for the
     *                          SampledSet.  May be <code>null</code>.
     * @param units             The units of the values.  May be
     *                          <code>null</code>.
     * @param errors            The uncertainty of the values.  May be
     *                          <code>null</code>.
     * @param copy              Whether or not to copy the values.
     * @return                  A SampledSet with the given attributes.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     */
    public static SampledSet newSampledSet(MathType type, float[][] values,
                                           int[] manifoldLengths,
                                           CoordinateSystem coordinateSystem,
                                           Unit[] units,
                                           ErrorEstimate[] errors,
                                           boolean copy)
            throws VisADException, RemoteException {

        if (values.length < 1) {
            throw new VisADException("no sample values");
        }

        for (int i = values.length; --i >= 1; ) {
            if (values[i].length != values[0].length) {
                throw new VisADException("values[i].lengths differ");
            }
        }

        SampledSet sampledSet;

        if (values[0].length == 1) {
            RealTuple realTuple;

            if (type instanceof RealType) {
                if (values.length != 1) {
                    throw new VisADException("values/type mismatch");
                }

                realTuple = new RealTuple(new Real[] {
                    new Real((RealType) type, values[0][0]) });
            } else if (type instanceof RealTupleType) {
                double[] samples = new double[values.length];

                for (int i = values.length; --i >= 0; ) {
                    samples[i] = values[i][0];
                }

                realTuple = new RealTuple((RealTupleType) type, samples);
            } else {
                throw new VisADException(
                    "type neither RealType nor RealTupleType");
            }

            sampledSet = new SingletonSet(realTuple, coordinateSystem, units,
                                          errors);
        } else if (manifoldLengths != null) {
            sampledSet = GriddedSet.create(type, values, manifoldLengths,
                                           coordinateSystem, units, errors);
        } else {
            sampledSet = (values.length == 1)
                         ? (IrregularSet) new Irregular1DSet(type, values,
                         coordinateSystem, units, errors)
                         : (values.length == 2)
                           ? (IrregularSet) new Irregular2DSet(type, values,
                           coordinateSystem, units, errors, (Delaunay) null)
                           : (values.length == 3)
                             ? (IrregularSet) new Irregular3DSet(type,
                             values, coordinateSystem, units, errors,
                             (Delaunay) null)
                             : new IrregularSet(type, values,
                             coordinateSystem, units, errors,
                             (Delaunay) null);
        }

        return sampledSet;
    }

    /**
     * Gets the specified sample of a VisAD Set.
     *
     * @param set               The set to have a sample returned.
     * @param index             The index of the sample to be returned.
     * @return                  The sample at the specified index.  Will
     *                          be empty if the index is out-of-bounds.
     *                          Will have a single component if the set is
     *                          one-dimensional.
     * @throws VisADException   Couldn't create necessary VisAD object.
     * @throws RemoteException  Java RMI failure.
     * @deprecated  use visad.util.DataUtility.getSample() now
     */
    public static RealTuple getSample(Set set, int index)
            throws VisADException, RemoteException {

        return visad.util.DataUtility.getSample(set, index);
    }

    /**
     * Takes indicated values from an array.
     *
     * @param values            The input array of values.
     * @param indexes           Which elements to extract.
     * @return                  A new array with the indicated elements.
     */
    public static float[] take(float[] values, int[] indexes) {

        float[] newValues = new float[indexes.length];

        for (int i = indexes.length; --i >= 0; ) {
            newValues[i] = values[indexes[i]];
        }

        return newValues;
    }

    /**
     * Indicates if an array is sorted.  The sense (increasing or decreasing)
     * is irrelevant and is non-strict.
     *
     * @param values            The array to check.
     * @return                  True if and only if the array is sorted.
     */
    public static boolean isSorted(float[] values) {

        boolean isSorted = true;

        if (values.length >= 3) {
            boolean increasing    = true;
            boolean haveDirection = false;
            float   previous      = values[0];

            for (int i = 1; i < values.length; ++i) {
                float value = values[i];

                if (haveDirection) {
                    if ((increasing && (value < previous))
                            || ( !increasing && (value > previous))) {
                        isSorted = false;

                        break;
                    }
                } else if (value != previous) {
                    haveDirection = true;
                    increasing    = value > previous;
                }

                previous = value;
            }
        }

        return isSorted;
    }

    /**
     * Indicates if an array is strictly sorted.  The sense (increasing or
     * decreasing) is irrelevant.
     *
     * @param values            The array to check.
     * @return                  True if and only if the array is strictly
     *                          sorted.
     */
    public static boolean isStrictlySorted(float[] values) {

        boolean isSorted = true;

        if (values.length >= 2) {
            boolean increasing    = true;
            boolean haveDirection = false;
            float   previous      = values[0];

            for (int i = 1; i < values.length; ++i) {
                float value = values[i];

                if (haveDirection) {
                    if ((increasing && (value <= previous))
                            || ( !increasing && (value >= previous))) {
                        isSorted = false;

                        break;
                    }
                } else if (value == previous) {
                    isSorted = false;

                    break;
                } else {
                    haveDirection = true;
                    increasing    = value > previous;
                }

                previous = value;
            }
        }

        return isSorted;
    }

    /** Comparator for a set of floats for increasing order */
    private static final java.util.Comparator increasingFloatComparator =
        new java.util.Comparator() {

        public int compare(Object o1, Object o2) {

            float v1 = ((float[]) o1)[0];
            float v2 = ((float[]) o2)[0];

            return (v1 == v1)
                   ? ((v2 == v2)
                      ? ((v1 < v2)
                         ? -1
                         : ((v1 > v2)
                            ? 1
                            : 0))
                      : +1)
                   : ((v2 == v2)
                      ? -1
                      : 0);
        }
    };

    /** Comparator for a set of floats for decreasing order */
    private static final java.util.Comparator decreasingFloatComparator =
        new java.util.Comparator() {

        public int compare(Object o1, Object o2) {

            float v1 = ((float[]) o1)[0];
            float v2 = ((float[]) o2)[0];

            return (v1 == v1)
                   ? ((v2 == v2)
                      ? ((v1 > v2)
                         ? -1
                         : ((v1 < v2)
                            ? 1
                            : 0))
                      : +1)
                   : ((v2 == v2)
                      ? -1
                      : 0);
        }
    };

    /** Comparator for a set of doubles for increasing order */
    private static final java.util.Comparator increasingDoubleComparator =
        new java.util.Comparator() {

        public int compare(Object o1, Object o2) {

            double v1 = ((double[]) o1)[0];
            double v2 = ((double[]) o2)[0];

            return (v1 == v1)
                   ? ((v2 == v2)
                      ? ((v1 < v2)
                         ? -1
                         : ((v1 > v2)
                            ? 1
                            : 0))
                      : +1)
                   : ((v2 == v2)
                      ? -1
                      : 0);
        }
    };

    /** Comparator for a set of doubles for decreasing order */
    private static final java.util.Comparator decreasingDoubleComparator =
        new java.util.Comparator() {

        public int compare(Object o1, Object o2) {

            double v1 = ((double[]) o1)[0];
            double v2 = ((double[]) o2)[0];

            return (v1 == v1)
                   ? ((v2 == v2)
                      ? ((v1 > v2)
                         ? -1
                         : ((v1 < v2)
                            ? 1
                            : 0))
                      : +1)
                   : ((v2 == v2)
                      ? -1
                      : 0);
        }
    };

    /**
     * Returns the element indexes that would sort an array.  The first
     * elements of the output index array will index the NaN-s in the input
     * array, if any.
     *
     * @param values         The array to examine.
     * @param increasing     Whether the sort is to be increasing or decreasing.
     * @return               The indexes of the elements that would sort the
     *                       array when taken in order.  The first indexes will
     *                       index the NaN-s of the input array, if any.
     */
    public static int[] sortedIndexes(float[] values, boolean increasing) {

        float[][] pairs = new float[values.length][2];

        for (int i = values.length; --i >= 0; ) {
            pairs[i][0] = values[i];
            pairs[i][1] = i;  // original index
        }

        java.util.Arrays.sort(pairs, increasing
                                     ? increasingFloatComparator
                                     : decreasingFloatComparator);

        int[] indexes = new int[pairs.length];

        for (int i = indexes.length; --i >= 0; ) {
            indexes[i] = (int) pairs[i][1];  // get original index
        }

        return indexes;
    }

    /**
     * Returns the element indexes that would sort an array.  The first
     * elements of the output index array will index the NaN-s in the input
     * array, if any.
     *
     * @param values         The array to examine.
     * @param increasing     Whether the sort is to be increasing or decreasing.
     * @return               The indexes of the elements that would sort the
     *                       array when taken in order.  The first indexes will
     *                       index the NaN-s of the input array, if any.
     */
    public static int[] sortedIndexes(double[] values, boolean increasing) {

        double[][] pairs = new double[values.length][2];

        for (int i = values.length; --i >= 0; ) {
            pairs[i][0] = values[i];
            pairs[i][1] = i;  // original index
        }

        java.util.Arrays.sort(pairs, increasing
                                     ? increasingDoubleComparator
                                     : decreasingDoubleComparator);

        int[] indexes = new int[pairs.length];

        for (int i = indexes.length; --i >= 0; ) {
            indexes[i] = (int) pairs[i][1];  // get original index
        }

        return indexes;
    }

    /**
     * Returns the element indexes that would strictly sort an array.  Nan-s in
     * the input array will be ignored.
     *
     * @param values         The array to examine.
     * @param increasing     Whether the sort is to be increasing or decreasing.
     * @return               The indexes of the elements that would strictly
     *                       sort the array when taken in order.
     */
    public static int[] strictlySortedIndexes(float[] values,
            final boolean increasing) {

        int[] indexes = sortedIndexes(values, increasing);

        /*
         * Remove NaN-s.
         */
        {
            int i;

            for (i = 0; i < indexes.length; i++) {
                if (values[indexes[i]] == values[indexes[i]]) {
                    break;
                }
            }

            if (i > 0) {
                int[] tmp = new int[indexes.length - i];
                System.arraycopy(indexes, i, tmp, 0, tmp.length);
                indexes = tmp;
            }
        }

        /*
         * Remove duplicates.
         */
        {
            boolean[] takeMask = new boolean[indexes.length];

            java.util.Arrays.fill(takeMask, true);

            int n = 0;

            for (int i = indexes.length; --i > 0; ) {
                if (values[indexes[i]] == values[indexes[i - 1]]) {
                    n++;

                    takeMask[i] = false;
                }
            }

            if (n > 0) {
                int[] tmp = new int[indexes.length - n];
                int   j   = 0;

                for (int i = 0; i < indexes.length; ++i) {
                    if (takeMask[i]) {
                        tmp[j++] = indexes[i];
                    }
                }

                indexes = tmp;
            }
        }

        return indexes;
    }

    /**
     * Returns the element indexes that would strictly sort an array.  Nan-s in
     * the input array will be ignored.
     *
     * @param values         The array to examine.
     * @param increasing     Whether the sort is to be increasing or decreasing.
     * @return               The indexes of the elements that would strictly
     *                       sort the array when taken in order.
     */
    public static int[] strictlySortedIndexes(double[] values,
            final boolean increasing) {

        int[] indexes = sortedIndexes(values, increasing);

        /*
         * Remove NaN-s.
         */
        {
            int i;

            for (i = 0; i < indexes.length; i++) {
                if (values[indexes[i]] == values[indexes[i]]) {
                    break;
                }
            }

            if (i > 0) {
                int[] tmp = new int[indexes.length - i];
                System.arraycopy(indexes, i, tmp, 0, tmp.length);
                indexes = tmp;
            }
        }

        /*
         * Remove duplicates.
         */
        {
            boolean[] takeMask = new boolean[indexes.length];

            java.util.Arrays.fill(takeMask, true);

            int n = 0;

            for (int i = indexes.length; --i > 0; ) {
                if (values[indexes[i]] == values[indexes[i - 1]]) {
                    n++;

                    takeMask[i] = false;
                }
            }

            if (n > 0) {
                int[] tmp = new int[indexes.length - n];
                int   j   = 0;

                for (int i = 0; i < indexes.length; ++i) {
                    if (takeMask[i]) {
                        tmp[j++] = indexes[i];
                    }
                }

                indexes = tmp;
            }
        }

        return indexes;
    }

    /**
     * Get the RealType from the RealTupleType that corresponds to one that
     * begins with start.
     *
     * @param type  type to search
     * @param start starting characters of name.  This is useful when
     *        Plain import creates names like "pres_0"
     * @return RealType that matches or null if no match
     *
     * @throws VisADException
     */
    public static RealType getRealType(RealTupleType type, String start)
            throws VisADException {
        RealType[] rTypes = type.getRealComponents();
        for (int i = 0; i < rTypes.length; i++) {
            if (rTypes[i].getName().toLowerCase().startsWith(start)) {
                return rTypes[i];
            }
        }
        return null;
    }


    /**
     * Parse a unit string specification and return the appropriate
     * unit. Handles some non-standard units.
     * @param unitIdentifier  unit specification
     * @return unit that corresponds to that specification
     * @throws VisADException  problem parsing unit
     */
    public static Unit parseUnit(String unitIdentifier)
            throws VisADException {
        return parseUnit(unitIdentifier, unitIdentifier);
    }



    /**
     * Parse a unit string specification and return the appropriate
     * unit. Handles some non-standard units.
     * @param unitIdentifier  unit specification
     * @param unitName Name to clone unit with
     * @return unit that corresponds to that specification
     *
     * @throws VisADException  problem parsing unit
     */
    public static Unit parseUnit(String unitIdentifier, String unitName)
            throws VisADException {

        if (unitIdentifier == null) {
            return null;
        }
        if (unitName == null) {
            unitName = unitIdentifier;
        }
        Unit u = null;
        // clean up ** and replace with nothing
        unitIdentifier = unitIdentifier.replaceAll("\\*\\*","");
        try {

            try {
                String realUnitName = MetUnits.makeSymbol(unitIdentifier);
                u = visad.data.units.Parser.parse(realUnitName);
            } catch (NoSuchUnitException nsu) {
                if (unitIdentifier.indexOf("_") >= 0) {
                    unitIdentifier = unitIdentifier.replace('_', ' ');
                    String realUnitName = MetUnits.makeSymbol(unitIdentifier);
                    u = visad.data.units.Parser.parse(realUnitName);
                } else {
                    throw new VisADException("No such unit:" + nsu);
                }
            }
        } catch (Exception exc) {
            throw new VisADException("Error parsing unit:" + exc);
        }
        try {
            u = u.clone(unitName);
        } catch (UnitException ue) {}
        return u;
    }


    /**
     * Return a formated date in UTC time. Uses DateTime.formatString()
     * with the UTC (GMT) time zone.
     * @param dt DateTime object
     * @param pattern format pattern
     * @return formatted date.
     * @deprecated  use UtcDate.formatUtcDate(DateTime, String)
     */
    public static String formatUtcDate(DateTime dt, String pattern) {
        return UtcDate.formatUtcDate(dt, pattern);
    }

    /**
     * _more_
     *
     * @param dttm _more_
     *
     * @return _more_
     *
     * @throws VisADException _more_
     */
    public static Date makeDate(DateTime dttm) throws VisADException {
        return new Date((long) dttm.getValue(CommonUnit.secondsSinceTheEpoch)
                        * 1000);
    }

    /**
     * _more_
     *
     * @param timesArray _more_
     *
     * @return _more_
     *
     * @throws VisADException _more_
     */
    public static List makeDates(DateTime[] timesArray)
            throws VisADException {
        List dates = new ArrayList();
        if (timesArray != null) {
            for (int i = 0; i < timesArray.length; i++) {
                dates.add(makeDate(timesArray[i]));
            }
        }
        return dates;
    }


    /**
     * Parses the given String and returns the Real (if possible)
     * that the string represents. The String may be of the form:
     * <pre>
     * dddd
     * dddd unit
     * dddd[unit]
     * </pre>
     * @param value The string value
     * @return The real or null if it could not convert the given value
     * @throws Exception  problem with unit spec or parsing.
     */
    public static Real toReal(String value) throws Exception {
        return toReal(value, "[","]");
    }

    public static Real toReal(String value, String unitOpener, String unitCloser) throws Exception {
        value = value.trim();
        //Check if there is a unit
        int idx = value.indexOf(unitOpener);
        if (idx < 0) {
            idx = value.indexOf(" ");
        }
        if (idx > 0) {
            //We have a unit
            String valueString = value.substring(0, idx).trim();
            String unitString  = value.substring(idx).trim();
            idx = unitString.indexOf(unitOpener);
            if (idx >= 0) {
                unitString = unitString.substring(1);
                unitString = unitString.substring(0, unitString.length() - 1);
            }
            //"utiltmp_"+unit.toString()
            //            System.err.println("V:"+valueString+":"+unitString);
            Unit   unit   = parseUnit(unitString);
            String rtName = "util_" + unit.toString();
            //      return new Real(RealType.Generic,Misc.parseValue(valueString),unit);
            return new Real(RealType.getRealType(rtName, unit),
                            Misc.parseValue(valueString), unit);
        }
        return new Real(Double.valueOf(value).doubleValue());
    }


    /**
     * Return the distance between the 2 points
     *
     * @param p1 Point 1
     * @param p2 Point 2
     * @return The distance
     */
    public static double distance(LatLonPoint p1, LatLonPoint p2) {
        double x1 = p1.getLongitude().getValue();
        double y1 = p1.getLatitude().getValue();
        double x2 = p2.getLongitude().getValue();
        double y2 = p2.getLatitude().getValue();
        if (x1 < 0.0) {
            x1 = -x1;
        }
        if (x2 < 0.0) {
            x2 = -x2;
        }
        return (Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)));
    }


    public static ucar.unidata.geoloc.LatLonPointImpl toLatLonPoint(visad.georef.LatLonPoint llp) 
        throws VisADException {
        return new LatLonPointImpl(llp.getLatitude().getValue(CommonUnit.degree),
                                   llp.getLongitude().getValue(CommonUnit.degree));
    }

    /**
     * Calculate the bearing between the 2 points.
     * See calculateBearing below.
     *
     * @param pt1 Point 1
     * @param pt2 Point 2
     * @param result Object to use if non-null
     *
     * @return The bearing
     * @throws VisADException When pt2 cannot be converted to the unit of pt1
     */
    public static ucar.unidata.geoloc.Bearing calculateBearing(
            LatLonPoint pt1, LatLonPoint pt2,
            ucar.unidata.geoloc.Bearing result)
            throws VisADException {
        visad.Unit latUnit = pt1.getLatitude().getUnit();
        visad.Unit lonUnit = pt1.getLongitude().getUnit();

        return ucar.unidata.geoloc.Bearing.calculateBearing(
            pt1.getLatitude().getValue(), pt1.getLongitude().getValue(),
            pt2.getLatitude().getValue(latUnit),
            pt2.getLongitude().getValue(lonUnit), result);
    }

    /**
     * Format a real value to a nice looking number
     *
     * @param r real to format
     * @return formatted value
     */
    public static String formatReal(Real r) {
        return Misc.format(r.getValue());
    }

    /**
     * Wrap a Real as a TwoFacedObject with a formatted label
     *
     * @param r real to wrap
     *
     * @return  Real with a nice label
     */
    public static TwoFacedObject labeledReal(Real r) {
        return labeledReal(r, false);
    }

    /**
     * Wrap a Real as a TwoFacedObject with a formatted label
     *
     * @param r real to wrap
     * @param includeUnit  if true, add the unit to the label
     *
     * @return  Real with a nice label
     */
    public static TwoFacedObject labeledReal(Real r, boolean includeUnit) {
        if (r == null) {
            return null;
        }
        String label = formatReal(r);
        if (includeUnit) {
            label = label + " " + r.getUnit();
        }
        return new TwoFacedObject(label, r);
    }

    /**
     * Get a FieldImpl with a domain of index and the range
     * of the datas.
     *
     * @param datas   Data objects for range.  Must all be of same type.
     * @param copy    true to copy data.
     * @return FieldImpl of index -&gt; datas.getType()
     *
     * @throws RemoteException Java RMI problem
     * @throws VisADException problem creating field
     */
    public static FieldImpl indexedField(Data[] datas, boolean copy)
            throws VisADException, RemoteException {
        if (datas == null) {
            throw new NullPointerException("indexedField: datas is null");
        }
        MathType     mt     = datas[0].getType();
        RealType     index  = RealType.getRealType("index");
        FunctionType ft     = new FunctionType(index, mt);
        Integer1DSet domain = new Integer1DSet(index, datas.length);
        FieldImpl    fi     = new FieldImpl(ft, domain);
        fi.setSamples(datas, false);
        return fi;
    }

    /**
     * Test
     *
     * @param args cmd line args
     */
    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            try {
                Real r = toReal(args[i]);
                System.err.println("" + r + ":" + r.getType());
            } catch (Exception exc) {
                System.err.println("error:" + args[i] + " " + exc);
            }
        }
    }

    /**
     * Return the distance from a bearing as Real
     *
     * @param el1 start location
     * @param el2 end location
     *
     * @return the distance between them
     *
     * @throws Exception problem calculating the distance
     */
    public static Real bearingDistance(EarthLocation el1, EarthLocation el2)
            throws Exception {

        LatLonPoint lllp = el1.getLatLonPoint();
        LatLonPoint rllp = el2.getLatLonPoint();
        visad.Unit latUnit = lllp.getLatitude().getUnit();
        visad.Unit lonUnit = lllp.getLongitude().getUnit();
        Bearing result =
            Bearing.calculateBearing(
                                     toLatLonPoint(lllp),
                                     toLatLonPoint(rllp),
                                     null);
        return new Real(Length.getRealType(), result.getDistance(),
                        CommonUnits.KILOMETER);
    }


    /**
     * Get the value of a Real in the unit specified
     *
     * @param r   Real
     * @param unitString  string representation of the return Unit
     *
     * @return the value in the unit
     *
     * @throws RemoteException Java RMI Exception
     * @throws VisADException  Unit problem
     */
    public static double getValueAs(Real r, String unitString)
            throws VisADException, RemoteException {
        try {
            Unit unit = ucar.visad.Util.parseUnit(unitString);
            return r.getValue(unit);
        } catch (Exception exc) {
            throw new VisADException("Error parsing unit:" + exc);
        }
    }



    /**
     * Get a Real with the specified value and Unit
     *
     * @param value  the value
     * @param unitString  the Unit
     *
     * @return  a Real with the value and Unit
     *
     * @throws RemoteException Java RMI Exception
     * @throws VisADException  Unit problem
     */
    public static Real getReal(double value, String unitString)
            throws VisADException, RemoteException {
        Unit unit = ucar.visad.Util.parseUnit(unitString);
        return getReal(value, unit);
    }


    /**
     * Get a Real with the specified value and Unit
     *
     * @param value  the value
     * @param unit   the Unit
     *
     * @return  a Real with the value and Unit
     *
     * @throws RemoteException Java RMI Exception
     * @throws VisADException  Unit problem
     */
    public static Real getReal(double value, Unit unit)
            throws VisADException, RemoteException {
        try {
            return new Real(getRealType(unit), value, unit);
        } catch (RuntimeException rexc) {
            throw rexc;
        } catch (Exception exc) {
            throw new VisADException("Error parsing unit:" + exc);
        }
    }

    /**
     * Get a realtype with the unit
     *
     * @param unit     unit
     * @return RealType of that unit (or null if unit incompatible with
     *                                a RealType with the same name)
     *
     * @throws RemoteException Java RMI Exception
     * @throws VisADException  Unit problem
     */
    public static RealType getRealType(Unit unit)
            throws VisADException, RemoteException {
        return makeRealType(REALTYPE_ROOT, unit);
    }


    /**
     * Get the position of the ray at a particular Z value.
     *
     * @param ray    ray to use
     * @param zValue Z value
     *
     * @return coordinates at Z value
     */
    public static double[] getRayPositionAtZ(VisADRay ray, double zValue) {
        if (Double.isNaN(zValue) || (zValue == ray.position[2])) {
            return ray.position;
        }
        if (ray.vector[2] == 0) {
            return ray.position;
        }
        //Misc.printArray("ray.vector", ray.vector);
        double r = (zValue - ray.position[2]) / ray.vector[2];
        //System.out.println("r = " + r);

        return new double[] { ray.position[0] + r * ray.vector[0],
                              ray.position[1] + r * ray.vector[1], zValue };

    }




    /**
     * A utility to strip off the unit name that gets appended to the real type name. This also
     * strips off the '(Text)' for text types
     *
     * @param name Initial real type name
     *
     * @return name with unit part stripped off
     */
    public static String cleanTypeName(String name) {
        int index = name.indexOf("[unit:");
        if (index >= 0) {
            name = name.substring(0, index);
        }
        name = ucar.unidata.util.StringUtil.replace(name, TEXT_IDENTIFIER,
                "");
        return name;
    }

    /**
     * Remove any of the extra unit suffixes that get added to the type name
     *
     * @param mathType the type
     *
     * @return the type name cleaned up
     */
    public static String cleanTypeName(MathType mathType) {
        return cleanTypeName(mathType.toString());
    }

    /**
     * Find the index of the MathType in the given tuple type whose cleaned name
     * is equals to the lookingFor parameter.
     *
     * @param tupleType Tuple type
     * @param lookingFor String to search for
     *
     * @return The index or -1 if not found
     */
    public static int getIndex(TupleType tupleType, String lookingFor) {
        MathType[] comps = tupleType.getComponents();
        for (int i = 0; i < comps.length; i++) {
            String name = cleanTypeName(comps[i]);
            if (name.equals(lookingFor)) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Create a RealType from the name and unit.  The unit is used as part
     * of the name so it should be unique.
     * @param name name of type
     * @param unit can be null
     * @return RealType of form &quot;name_unit&quot;
     * @throws VisADException  unable to create the RealType (shouldn't happen)
     */
    public static RealType makeRealType(String name, Unit unit)
            throws VisADException {
        return makeRealType(name, null, unit);
    }


    public static RealType makeRealType(String name, String alias, Unit unit)
        throws VisADException {
        RealType type    = null;
        String   newname = cleanName(name) + "[unit:" + ((unit == null)
                ? "null"
                : cleanName(unit.toString())) + "]";

        type = RealType.getRealType(newname, unit);
        if (type == null) {  // try one more time
            type = RealType.getRealType(newname + "_" + typeCnt++, unit);
        }
        if (type == null) {
            throw new VisADException(
                "couldn't create RealType with units compatible to "
                + unit.toString());
        }
        if(alias!=null) {
            type.alias(alias);
        }
        return type;
    }

    /**
     * Format an LatLonPoint as a lat/lon string.
     *
     * @param llp  LatLonPoint to format
     * @return The formatted LatLonPoint
     */
    public static String formatLatLonPoint(LatLonPoint llp) {

        StringBuffer buf = new StringBuffer();
        buf.append("Lat: ");
        try {
            buf.append(Convert.shortString(llp.getLatitude().getValue()));
        } catch (Exception e) {
            buf.append(" ");
        }
        buf.append(" Lon: ");
        try {
            buf.append(Convert.shortString(llp.getLongitude().getValue()));
        } catch (Exception e) {
            buf.append(" ");
        }
        return buf.toString();

    }


    /**
     * Utility to make an earth location with a 0 altitude
     *
     * @param lat latitude
     * @param lon longitude
     *
     * @return earth location
     *
     * @throws Throwable On badness
     */
    public static EarthLocation makeEarthLocation(double lat, double lon)
            throws Throwable {
        return new EarthLocationTuple(new Real(RealType.Latitude, lat),
                                      new Real(RealType.Longitude, lon),
                                      new Real(RealType.Altitude, 0.0));
    }


    /**
     * Utility to make an earth location with a 0 altitude
     *
     * @param llp lat/lon
     *
     * @return earth location
     *
     * @throws Throwable On badness
     */
    public static EarthLocation makeEarthLocation(LatLonPoint llp)
            throws Throwable {
        return ucar.visad.Util.makeEarthLocation(
            llp.getLatitude().getValue(), llp.getLongitude().getValue());
    }



    /**
     * Format a EarthLocation as a lat/lon/(alt) string.
     *
     * @param el  EarthLocation to format
     * @param includeAlt  include Altitude in the return
     * @return The formatted lat/lon/alt
     */
    public static String formatEarthLocation(EarthLocation el,
                                             boolean includeAlt) {
        StringBuffer buf = new StringBuffer();
        try {
            buf.append(formatLatLonPoint(el.getLatLonPoint()));
        } catch (Exception e) {
            return "";
        }
        if (includeAlt) {
            buf.append(" Alt: ");
            try {
                buf.append(formatAltitude(el.getAltitude()));
            } catch (Exception e) {
                buf.append(" ");
            }
        }
        return buf.toString();

    }

    /**
     * Format an Altitude
     *
     * @param alt The altitude
     * @return The formatted alt
     */
    public static String formatAltitude(Real alt) {
        return Convert.shortString(alt.getValue()) + " " + alt.getUnit();
    }

    /**
     * Wrapper for JPythonMethods.dumpTypes(Data)
     *
     * @param d  the Data object
     *
     * @throws VisADException  problem dumping the data
     */
    public static void dumpTypes(Data d) throws VisADException {
        try {
            visad.python.JPythonMethods.dumpTypes(d);
        } catch (RemoteException re) {}
    }

    /**
     * Get the VisAD Virtual World x,y coordinates for the given screen coords
     * @param display  the display
     * @param x  the canvas X coordinate
     * @param y  the canvas Y coordinate
     * @param retVals  the return array (may be null)
     *
     * @return an array of the x,y,z coordinates or null
     *
     */
    public static double[] getVWorldCoords(DisplayImpl display, int x, int y,
                                           double[] retVals) {
        if (retVals == null) {
            retVals = new double[3];
        }
        if (display == null) {
            return retVals;
        }
        MouseBehavior   behavior = display.getMouseBehavior();
        DisplayRenderer renderer = display.getDisplayRenderer();
        if (renderer != null) {
            boolean isPerspective =
                display.getGraphicsModeControl().getProjectionPolicy()
                == View.PERSPECTIVE_PROJECTION;

            if (renderer instanceof DisplayRendererJ3D) {
                DisplayRendererJ3D j3drend = (DisplayRendererJ3D) renderer;
                VisADCanvasJ3D     canvas  = j3drend.getCanvas();
                if (canvas != null) {

                    Point3d position1 = new Point3d();
                    canvas.getPixelLocationInImagePlate(x, y, position1);

                    /*
                      if ((display != null)
                      && (display.getGraphicsModeControl() != null)) {
                      // hack to move text closer to eye
                      if (display.getGraphicsModeControl()
                      .getProjectionPolicy() == View
                      .PERSPECTIVE_PROJECTION) {
                      Point3d left_eye  = new Point3d();
                      Point3d right_eye = new Point3d();
                      canvas.getLeftEyeInImagePlate(left_eye);
                      canvas.getRightEyeInImagePlate(right_eye);
                      Point3d eye =
                      new Point3d((left_eye.x + right_eye.x) / 2.0,
                      (left_eye.y + right_eye.y) / 2.0,
                      (left_eye.z + right_eye.z) / 2.0);
                      double alpha = 0.3;
                      position1.x = alpha * position1.x
                      + (1.0 - alpha) * eye.x;
                      position1.y = alpha * position1.y
                      + (1.0 - alpha) * eye.y;
                      position1.z = alpha * position1.z
                      + (1.0 - alpha) * eye.z;
                      }
                      }
                    */

                    Transform3D t = new Transform3D();
                    canvas.getImagePlateToVworld(t);
                    t.transform(position1);
                    double scaleFactor = renderer.getMode2D()
                                         ? ProjectionControl.SCALE2D
                                         : .5;
                    retVals[0] = position1.x / scaleFactor;
                    retVals[1] = position1.y / scaleFactor;
                    retVals[2] = position1.z / scaleFactor;
                    // System.out.println("j3d way " + retVals[0] + ","
                    //                    + retVals[1] + "," + retVals[2]);
                }

            } else {
                //alternate method that works for no rotation
                double[] matrix = display.getProjectionControl().getMatrix();
                //getDisplayMaster().printMatrix("matrix", matrix);
                boolean  is2D  = renderer.getMode2D();
                double[] rot   = new double[3];
                double[] scale = new double[3];
                double[] trans = new double[3];
                behavior.instance_unmake_matrix(rot, scale, trans, matrix);
                double defScale = is2D
                                  ? ProjectionControl.SCALE2D
                                  : .5;
                double scalex   = scale[0] / defScale;
                double scaley   = scale[1] / defScale;
                double transx   = trans[0] / defScale;
                double transy   = trans[1] / defScale;

                // starting position
                double[] ray = getRayPositionAtZ(behavior.findRay(x, y),
                                   -1.0);
                retVals[0] = ray[0] * scalex + transx;
                retVals[1] = ray[1] * scaley + transy;
                retVals[2] = 0.0;
                //System.out.println("old way " + retVals[0] + "," + retVals[1]
                //                   + "," + retVals[2]);

            }
        }
        return retVals;
    }


    /**
     * Create a VisAD Data object from the given Image
     * @param  image   image to use
     * @param  makeNansForAnyAlpha   If true then we make a field with an alpha
     *   value and we turn the other values into nan-s
     * @return a FlatField representation of the image
     *
     * @throws IOException _more_
     * @throws VisADException _more_
     */
    public static FlatField makeField(Image image,
                                      boolean makeNansForAnyAlpha)
            throws IOException, VisADException {

        if (image == null) {
            throw new VisADException("image cannot be null");
        }
        ImageHelper ih = new ImageHelper();

        // determine image height and width
        int width  = -1;
        int height = -1;
        do {
            if (width < 0) {
                width = image.getWidth(ih);
            }
            if (height < 0) {
                height = image.getHeight(ih);
            }
            if (ih.badImage || ((width >= 0) && (height >= 0))) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        } while (true);
        if (ih.badImage) {
            throw new IOException("Not an image");
        }

        // extract image pixels
        int   numPixels = width * height;
        int[] words     = new int[numPixels];

        PixelGrabber grabber = new PixelGrabber(image.getSource(), 0, 0,
                                   width, height, words, 0, width);

        try {
            grabber.grabPixels();
        } catch (InterruptedException e) {}

        ColorModel cm        = grabber.getColorModel();

        float[]    red_pix   = new float[numPixels];
        float[]    green_pix = new float[numPixels];
        float[]    blue_pix  = new float[numPixels];

        for (int i = 0; i < numPixels; i++) {
            red_pix[i]   = cm.getRed(words[i]);
            green_pix[i] = cm.getGreen(words[i]);
            blue_pix[i]  = cm.getBlue(words[i]);
        }
        boolean opaque = true;
        if (makeNansForAnyAlpha) {
            float   alpha_pix;
            boolean hasAlpha = cm.hasAlpha();
            for (int i = 0; i < numPixels; i++) {
                if (hasAlpha) {
                    alpha_pix = cm.getAlpha(words[i]);
                } else {
                    alpha_pix = 255.0f;
                }
                if (alpha_pix != 255.0f) {
                    red_pix[i]   = Float.NaN;
                    green_pix[i] = Float.NaN;
                    blue_pix[i]  = Float.NaN;
                    opaque       = false;
                }
            }
        }

        //System.out.println("opaque = " + opaque);

        // build FlatField
        RealType      line              = RealType.getRealType("ImageLine");
        RealType      element           =
            RealType.getRealType("ImageElement");
        RealType      c_red             = RealType.getRealType("Red");
        RealType      c_green           = RealType.getRealType("Green");
        RealType      c_blue            = RealType.getRealType("Blue");
        RealType      c_alpha           = RealType.getRealType("Alpha");

        RealType[]    c_all = new RealType[] { c_red, c_green, c_blue };
        RealTupleType radiance          = new RealTupleType(c_all);

        RealType[]    domain_components = { element, line };
        RealTupleType image_domain      =
            new RealTupleType(domain_components);
        Linear2DSet domain_set = new Linear2DSet(image_domain, 0.0,
                                     (float) (width - 1.0), width,
                                     (float) (height - 1.0), 0.0, height);
        FunctionType image_type = new FunctionType(image_domain, radiance);

        FlatField    field      = new FlatField(image_type, domain_set);

        float[][]    samples    = new float[][] {
            red_pix, green_pix, blue_pix
        };
        try {
            field.setSamples(samples, false);
        } catch (RemoteException e) {
            throw new VisADException("Couldn't finish image initialization");
        }

        return field;

    }



    /**
     * _more_
     *
     * @param lat1 _more_
     * @param lon1 _more_
     * @param lat2 _more_
     * @param lon2 _more_
     *
     * @return _more_
     *
     * @throws VisADException _more_
     */
    public static MapProjection makeMapProjection(double lat1, double lon1,
            double lat2, double lon2)
            throws VisADException {

        double minX = Math.max(-180, Math.min(lon1, lon2));
        double maxX = Math.min(180, Math.max(lon1, lon2));
        double minY = Math.max(-90, Math.min(lat1, lat2));
        double maxY = Math.min(90, Math.max(lat1, lat2));
        double degX = maxX - minX;
        double degY = maxY - minY;
        //Try to make the box square
        if (degY > degX) {
            double delta = degY - degX;
            minX -= delta / 2;
            maxX += delta / 2;
        } else if (degX > degY) {
            double delta = degX - degY;
            minY -= delta / 2;
            maxY += delta / 2;
        }
        minX = Math.max(-180, minX);
        maxX = Math.min(180, maxX);
        minY = Math.max(-90, minY);
        maxY = Math.min(90, maxY);

        double maxDegrees = Math.max(maxX - minX, maxY - minY);
        Rectangle2D.Float rect = new Rectangle2D.Float((float) minX,
                                     (float) minY, (float) (maxX - minX),
                                     (float) (maxY - minY));

        return new TrivialMapProjection(RealTupleType.SpatialEarth2DTuple,
                                        rect);
    }



    /**
     *  Use reflection to find the Method with name "set" + Name.
     *  If found then convert the given value to the appropriate type and invoke the method.
     *
     *  @param object The object to invoke the set property method on.
     *  @param name The name of the method.
     *  @param value The String representation of the value to set.
     * @param ignoreError If true then don't print out an error
     *
     *
     * @return Did we successfully set the property
     *
     * @throws Exception _more_
     */
    public static boolean propertySet(Object object, String name,
                                      Object value, boolean ignoreError)
            throws Exception {

        if (Misc.setProperty(object, name, value, true)) {
            return true;
        }
        String methodName = "set" + name.substring(0, 1).toUpperCase()
                            + name.substring(1);
        Method method = Misc.findMethod(object.getClass(), methodName,
                                        new Class[] { null });
        if (method == null) {
            if ( !ignoreError) {
                System.err.println("could not find method:" + methodName
                                   + ": on class:"
                                   + object.getClass().getName());
            }
            return false;
        }
        Object argument  = null;
        Class  paramType = method.getParameterTypes()[0];
        if (paramType.equals(Real.class)) {
            argument = toReal(value.toString());
        } else if (paramType.equals(Unit.class)) {
            argument = parseUnit(value.toString());
        }
        if (argument != null) {
            method.invoke(object, new Object[] { argument });
            return true;
        }
        return false;
    }

    /**
     * This makes a field of T->range for the times in the list.
     * If the times list if null or empty it just returns the range
     *
     * @param range The range
     * @param times List of times
     * @return The time field or the range if times is null
     *
     * @throws RemoteException _more_
     * @throws VisADException _more_
     */
    public static Data makeTimeField(Data range, List times)
            throws VisADException, RemoteException {
        if ((times == null) || (times.size() == 0)) {
            return range;
        }
        FieldImpl fi      = null;
        Set       timeSet = makeTimeSet(times);
        for (int i = 0; i < times.size(); i++) {
            if (fi == null) {
                DateTime dttm;
                Object obj = times.get(i);
                if(obj instanceof DateTime) {
                    dttm = (DateTime)  obj;
                } else if(obj instanceof Date) {
                    dttm = new DateTime((Date)obj);
                } else {
                    throw new IllegalArgumentException("Unknown date type:" + obj);
                }
                fi = new FieldImpl(new FunctionType(dttm.getType(),
                        range.getType()), timeSet);
            }
            fi.setSample(i, range, false, false);
        }
        return fi;
    }



    public static FieldImpl makeTimeField(List ranges, List times)
            throws VisADException, RemoteException {
        FieldImpl fi      = null;
        Hashtable timeToData = new Hashtable();
        for(int i=0;i<times.size();i++) {
            timeToData.put(times.get(i), ranges.get(i));
        }

        Set       timeSet = makeTimeSet(times);
        int setSize = timeSet.getLength();

        Object obj = times.get(0);
        DateTime dttm=null;
        if(obj instanceof DateTime) {
            dttm = (DateTime)  obj;
        } else if(obj instanceof Date) {
            dttm = new DateTime((Date)obj);
        } else {
            throw new IllegalArgumentException("Unknown date type:" + obj);
        }

        for (int i = 0; i < setSize;i++) {
            Object time = timeSet.__getitem__(i);
            Data range = (Data)timeToData.get(time);
            if (fi == null) {
                fi = new FieldImpl(new FunctionType(dttm.getType(),
                        range.getType()), timeSet);
            }
            fi.setSample(i, range, false, false);
        }
        return fi;
    }





    /**
     * This makes a field of T->range for the times in the list.
     * If the times list if null or empty it just returns the range
     *
     * @param range The range
     * @param times List of times
     * @return The time field or the range if times is null
     *
     * @throws RemoteException _more_
     * @throws VisADException _more_
     */
    public static Data makeTimeRangeField(Data range, List times)
            throws VisADException, RemoteException {
        if ((times == null) || (times.size() == 0)) {
            return range;
        }
        times = new ArrayList(times);
        FieldImpl fi      = null;
        DateTime  minDate = null;
        DateTime  maxDate = null;
        for (int i = 0; i < times.size(); i++) {
            DateTime dttm = (DateTime) times.get(i);
            if ((minDate == null) || (dttm.getValue() < minDate.getValue())) {
                minDate = dttm;
            }
            if ((maxDate == null) || (dttm.getValue() > maxDate.getValue())) {
                maxDate = dttm;
            }

        }

        times.add(0, new DateTime(minDate.getValue() - TIMERANGE_DELTA,
                                  minDate.getUnit()));
        times.add(times.size(),
                  new DateTime(maxDate.getValue() + TIMERANGE_DELTA,
                               maxDate.getUnit()));

        Set timeSet = makeTimeSet(times);
        for (int i = 0; i < times.size(); i++) {
            if (fi == null) {
                DateTime dttm = (DateTime) times.get(i);
                fi = new FieldImpl(new FunctionType(dttm.getType(),
                        range.getType()), timeSet);
            }
            if ((i != 0) && (i != times.size() - 1)) {
                fi.setSample(i, range, false, false);
            }
        }
        return fi;
    }



    /**
     * _more_
     *
     * @param set _more_
     * @param value _more_
     *
     * @return _more_
     *
     * @throws RemoteException _more_
     * @throws VisADException _more_
     */
    public static int findIndex(Set set, Real value)
            throws VisADException, RemoteException {
        Unit setUnit = set.getSetUnits()[0];
        if (Unit.canConvert(value.getUnit(), setUnit)) {
            double valueVal = value.getValue(setUnit);
            int    index    = set.doubleToIndex(new double[][] {
                new double[] { valueVal }
            })[0];
            return index;
            //            if (index >= 0) {
            //                RealTuple rt = DataUtility.getSample(set, index);
            //                DateValue dataValue =
            //                    new DateValue((Real) rt.getComponent(0));
            //            }
        }
        return -1;

    }


    /**
     * Convert the elements in the set to a List
     *
     * @param set The set
     * @return The list if items in the set
     *
     * @throws RemoteException _more_
     * @throws VisADException _more_
     */
    public static List toList(Set set)
            throws VisADException, RemoteException {
        List l = new ArrayList();
        if (set == null) {
            return l;
        }
        for (int i = 0; i < set.getLength(); i++) {
            l.add(set.__getitem__(i));
        }
        return l;
    }


    /**
     * _more_
     *
     * @param times _more_
     *
     * @return _more_
     *
     * @throws RemoteException _more_
     * @throws VisADException _more_
     */
    public static Set makeTimeSet(List times)
            throws VisADException, RemoteException {
        return DateTime.makeTimeSet(
            (DateTime[]) times.toArray(new DateTime[times.size()]));
    }

    /**
     * Get graphics configuration for the screen
     *
     * @param d  the GraphicsDevice
     * @param is3D  true for a Java 3D display
     * @param useStereo true if a stereo display (is3D must also be true)
     *
     * @return the perferred config
     */
    public static GraphicsConfiguration getPreferredConfig(GraphicsDevice d,
            boolean is3D, boolean useStereo) {
        try {
            if (d == null) {
                GraphicsEnvironment e =
                    GraphicsEnvironment.getLocalGraphicsEnvironment();
                d = e.getDefaultScreenDevice();
            }
            GraphicsConfigTemplate template = null;
            if (is3D) {
                template = new GraphicsConfigTemplate3D();

                if (useStereo) {
                    ((GraphicsConfigTemplate3D) template).setStereo(
                        GraphicsConfigTemplate3D.PREFERRED);
                }
            }
            if (template == null) {
                return d.getDefaultConfiguration();
            } else {
                return d.getBestConfiguration(template);
            }
        } catch (HeadlessException he) {
            return null;
        }
    }

    /**
     * Export the data object as a netCDF file
     * @param data   the VisAD data to export
     * @throws Exception  can't write data as netCDF
     */
    public static boolean exportAsNetcdf(Data data) throws Exception {
        String filename = FileManager.getWriteFile(FileManager.FILTER_NETCDF,
                              FileManager.SUFFIX_NETCDF);
        if (filename == null) {
            return false;
        }
        Plain p = new Plain();
        p.save(filename, data, true);
        return true;
    }



    /**
     * _more_
     *
     * @param lon1 _more_
     * @param lon2 _more_
     * @param length1 _more_
     * @param lat1 _more_
     * @param lat2 _more_
     * @param length2 _more_
     * @param fill _more_
     * @param unitString _more_
     *
     * @return _more_
     *
     * @throws RemoteException _more_
     * @throws VisADException _more_
     */
    public static FlatField makeField(float lon1, float lon2, int length1,
                                      float lat1, float lat2, int length2,
                                      float fill, String unitString)
            throws VisADException, RemoteException {
        Unit unit = parseUnit(unitString);
        FunctionType type =
            new FunctionType(RealTupleType.SpatialEarth2DTuple,
                             getRealType(unit));
        Linear2DSet domain = new Linear2DSet(type.getDomain(), lon1, lon2,
                                             length1, lat1, lat2, length2);

        FlatField field = new FlatField(type, domain);
        float[][] data  = new float[1][length1 * length2];
        for (int i = 0; i < length1; i++) {
            for (int j = 0; j < length2; j++) {
                data[0][i + length1 * j] = fill;
            }
        }
        field.setSamples(data, false);
        return field;


    }

    public static GriddedSet makeEarthDomainSet(float[]lats, float[]lons, float[]alts) throws VisADException {
        float[][] values = new float[alts!=null?3:2][];
        values[0] = lats;
        values[1] = lons;
        if(alts!=null) {
            values[2] = alts;
            return new Gridded3DSet(RealTupleType.LatitudeLongitudeAltitude,
                                    values, values[0].length);
        } 
        return new Gridded2DSet(RealTupleType.LatitudeLongitudeTuple,
                                values, values[0].length);
    }

    public static ucar.unidata.geoloc.LatLonPoint toLLP(EarthLocation el) {
        return toLLP(el.getLatLonPoint());
    }

    public static ucar.unidata.geoloc.LatLonPoint toLLP(visad.georef.LatLonPoint llp) {
        return new ucar.unidata.geoloc.LatLonPointImpl(llp.getLatitude().getValue(),
                                                    llp.getLongitude().getValue());

    }


}

