/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers.body.resolve

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.BodyResolveComponents
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.toSymbol
import org.jetbrains.kotlin.fir.visibilityChecker
import org.jetbrains.kotlin.resolve.calls.TypeVisibilityFilter
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

class FirTypeVisibilityFilter(
    private val components: BodyResolveComponents,
) : TypeVisibilityFilter {
    private val session: FirSession = components.session

    override fun isAccessible(type: KotlinTypeMarker): Boolean {
        require(type is ConeKotlinType)
        val symbol = type.toSymbol(session) as? FirClassLikeSymbol<*> ?: return true
        return session.visibilityChecker.isClassLikeVisible(symbol.fir, session, components.file, components.containingDeclarations)
    }
}