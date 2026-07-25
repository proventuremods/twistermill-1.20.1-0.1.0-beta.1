package com.proventure.twistermill.compat.framedblocks;

import xfacthd.framedblocks.api.camo.CamoContainer;
import xfacthd.framedblocks.api.camo.CamoContainerFactory;

public final class TwisterMillFlatCamoContainer
        extends CamoContainer<TwisterMillFlatCamoContent, TwisterMillFlatCamoContainer> {

    public TwisterMillFlatCamoContainer(TwisterMillFlatCamoType type) {
        super(new TwisterMillFlatCamoContent(type));
    }

    public TwisterMillFlatCamoType type() {
        return getContent().type();
    }

    @Override
    public boolean canRotateCamo() {
        return false;
    }

    @Override
    public TwisterMillFlatCamoContainer rotateCamo() {
        return this;
    }

    @Override
    public int hashCode() {
        return type().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TwisterMillFlatCamoContainer other && other.type() == type();
    }

    @Override
    public CamoContainerFactory<TwisterMillFlatCamoContainer> getFactory() {
        return TwisterMillFramedBlocksCompat.flatCamoFactory();
    }
}
