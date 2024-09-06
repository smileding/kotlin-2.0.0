/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtEnumEntry;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;
import org.jetbrains.kotlin.psi.stubs.KotlinClassOrObjectStub;
import org.jetbrains.kotlin.psi.stubs.KotlinClassStub;
import org.jetbrains.kotlin.psi.stubs.StubUtils;
import org.jetbrains.kotlin.psi.stubs.impl.KotlinClassStubImpl;
import org.jetbrains.kotlin.psi.stubs.impl.Utils;

import java.io.IOException;
import java.util.List;

public class KtClassElementType extends KtStubElementType<KotlinClassStub, KtClass> {
    public KtClassElementType(@NotNull @NonNls String debugName) {
        super(debugName, KtClass.class, KotlinClassStub.class);
    }

    @NotNull
    @Override
    public KtClass createPsi(@NotNull KotlinClassStub stub) {
        return !stub.isEnumEntry() ? new KtClass(stub) : new KtEnumEntry(stub);
    }

    @NotNull
    @Override
    public KtClass createPsiFromAst(@NotNull ASTNode node) {
        return node.getElementType() != KtStubElementTypes.ENUM_ENTRY ? new KtClass(node) : new KtEnumEntry(node);
    }

    private static boolean isNewPlaceForBodyGeneration(KotlinClassOrObjectStub stub) {
        if (!(stub instanceof KotlinClassStub)) return false;
        return ((KotlinClassStub)stub).isNewPlaceForBodyGeneration();
    }

    @NotNull
    @Override
    public KotlinClassStub createStub(@NotNull KtClass psi, StubElement parentStub) {
        FqName fqName = KtPsiUtilKt.safeFqNameForLazyResolve(psi);
        boolean isEnumEntry = psi instanceof KtEnumEntry;
        List<String> superNames = KtPsiUtilKt.getSuperNames(psi);
        ClassId classId = StubUtils.createNestedClassId(parentStub, psi);
        return new KotlinClassStubImpl(
                getStubType(isEnumEntry), (StubElement<?>) parentStub,
                StringRef.fromString(fqName != null ? fqName.asString() : null), classId,
                StringRef.fromString(psi.getName()),
                Utils.INSTANCE.wrapStrings(superNames),
                psi.isInterface(), isEnumEntry, isNewPlaceForBodyGeneration(psi.getStub()), psi.isLocal(), psi.isTopLevel()
        );
    }

    @Override
    public void serialize(@NotNull KotlinClassStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getName());

        FqName fqName = stub.getFqName();
        dataStream.writeName(fqName == null ? null : fqName.asString());

        StubUtils.serializeClassId(dataStream, stub.getClassId());

        dataStream.writeBoolean(stub.isInterface());
        dataStream.writeBoolean(stub.isEnumEntry());
        dataStream.writeBoolean(stub.isNewPlaceForBodyGeneration());
        dataStream.writeBoolean(stub.isLocal());
        dataStream.writeBoolean(stub.isTopLevel());

        List<String> superNames = stub.getSuperNames();
        dataStream.writeVarInt(superNames.size());
        for (String name : superNames) {
            dataStream.writeName(name);
        }
    }

    @NotNull
    @Override
    public KotlinClassStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        StringRef name = dataStream.readName();
        StringRef qualifiedName = dataStream.readName();

        ClassId classId = StubUtils.deserializeClassId(dataStream);

        boolean isTrait = dataStream.readBoolean();
        boolean isEnumEntry = dataStream.readBoolean();
        boolean isNewPlaceForBodyGeneration = dataStream.readBoolean();
        boolean isLocal = dataStream.readBoolean();
        boolean isTopLevel = dataStream.readBoolean();

        int superCount = dataStream.readVarInt();
        StringRef[] superNames = StringRef.createArray(superCount);
        for (int i = 0; i < superCount; i++) {
            superNames[i] = dataStream.readName();
        }

        return new KotlinClassStubImpl(
                getStubType(isEnumEntry), (StubElement<?>) parentStub, qualifiedName,classId, name, superNames,
                isTrait, isEnumEntry, isNewPlaceForBodyGeneration, isLocal, isTopLevel
        );
    }

    @Override
    public void indexStub(@NotNull KotlinClassStub stub, @NotNull IndexSink sink) {
        StubIndexService.getInstance().indexClass(stub, sink);
    }

    public static KtClassElementType getStubType(boolean isEnumEntry) {
        return isEnumEntry ? KtStubElementTypes.ENUM_ENTRY : KtStubElementTypes.CLASS;
    }
}
