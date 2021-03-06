package org.arend.ext;

import org.arend.ext.core.definition.*;

/**
 * Provides access to the definitions in the prelude.
 */
public interface ArendPrelude {
  CoreDataDefinition getInterval();
  CoreConstructor getLeft();
  CoreConstructor getRight();
  CoreFunctionDefinition getSqueeze();
  CoreFunctionDefinition getSqueezeR();
  CoreDataDefinition getNat();
  CoreConstructor getZero();
  CoreConstructor getSuc();
  CoreFunctionDefinition getPlus();
  CoreFunctionDefinition getMul();
  CoreFunctionDefinition getMinus();
  CoreDataDefinition getFin();
  CoreFunctionDefinition getFinFromNat();
  CoreDataDefinition getInt();
  CoreConstructor getPos();
  CoreConstructor getNeg();
  CoreFunctionDefinition getCoerce();
  CoreFunctionDefinition getCoerce2();
  CoreDataDefinition getPath();
  CoreFunctionDefinition getEquality();
  CoreConstructor getPathCon();
  CoreFunctionDefinition getInProp();
  CoreFunctionDefinition getIdp();
  CoreFunctionDefinition getAt();
  CoreFunctionDefinition getIso();
  CoreFunctionDefinition getDivMod();
  CoreFunctionDefinition getDiv();
  CoreFunctionDefinition getMod();
  CoreFunctionDefinition getDivModProp();
  CoreClassDefinition getArray();
  CoreClassField getArrayElementsType();
  CoreClassField getArrayLength();
  CoreClassField getArrayAt();
  CoreFunctionDefinition getEmptyArray();
  CoreFunctionDefinition getArrayCons();
  CoreFunctionDefinition getArrayIndex();
}
