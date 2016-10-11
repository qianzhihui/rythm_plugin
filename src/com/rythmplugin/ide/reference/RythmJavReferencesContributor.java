package com.rythmplugin.ide.reference;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.rythmplugin.file.RythmFileTypeFactory;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

/**
 * Created by mpl on 10.10.2016.
 */
public class RythmJavReferencesContributor extends PsiReferenceContributor {

    public static final PsiJavaElementPattern.Capture<PsiLiteralExpression> STYLESHEET_PATTERN =
            literalExpression().methodCallParameter(psiMethod()).and(new FilterPattern(new ElementFilter() {
                public boolean isAcceptable(Object element, PsiElement context) {
                    final PsiExpression psiExpression = getParentElement((PsiLiteralExpression)context);

                    return false;
                }

                public boolean isClassAcceptable(Class hintClass) {
                    return true;
                }
            }));

    public static PsiExpression getParentElement(PsiLiteralExpression context) {
        PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(context, PsiMethodCallExpression.class);
        final Object value = context.getValue();
        if (value instanceof String && ((String)value).endsWith(".bss")) {
            final PsiExpressionList addArgumentsList = PsiTreeUtil.getParentOfType(methodCallExpression, PsiExpressionList.class);
            methodCallExpression = PsiTreeUtil.getParentOfType(addArgumentsList, PsiMethodCallExpression.class);
        }
        if (methodCallExpression != null) {
            PsiMethod psiMethod = methodCallExpression.resolveMethod();
            if (psiMethod != null) {
                if ("getResource".equals(psiMethod.getName())) {
                    final PsiExpressionList addArgumentsList = PsiTreeUtil.getParentOfType(methodCallExpression, PsiExpressionList.class);
                    methodCallExpression = PsiTreeUtil.getParentOfType(addArgumentsList, PsiMethodCallExpression.class);
                    psiMethod = methodCallExpression != null ? methodCallExpression.resolveMethod() : null;
                    if (psiMethod == null) return null;
                }
                if ("add".equals(psiMethod.getName())) {
                    final PsiClass containingClass = psiMethod.getContainingClass();
                    if (containingClass != null ) {
                        final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
                        if (qualifierExpression instanceof PsiMethodCallExpression) {
                            final PsiReferenceExpression getStylesheetsMethodExpression = ((PsiMethodCallExpression)qualifierExpression).getMethodExpression();
                            if ("getStylesheets".equals(getStylesheetsMethodExpression.getReferenceName())) {
                                return getStylesheetsMethodExpression.getQualifierExpression();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static final PsiJavaElementPattern.Capture<PsiLiteralExpression> FXML_PATTERN =
            literalExpression().methodCallParameter(psiMethod().withName("getResource")).and(new FilterPattern(new ElementFilter() {
                public boolean isAcceptable(Object element, PsiElement context) {
                    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)context;
                    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(literalExpression, PsiMethodCallExpression.class);
                    final PsiCallExpression superCall = PsiTreeUtil.getParentOfType(callExpression, PsiCallExpression.class, true);
                    if (superCall instanceof PsiMethodCallExpression) {
                        final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)superCall).getMethodExpression();
                        if ("load".equals(methodExpression.getReferenceName())) {
                            final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
                            PsiClass psiClass = null;
                            if (qualifierExpression instanceof PsiReferenceExpression) {
                                final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
                                if (resolve instanceof PsiClass) {
                                    psiClass = (PsiClass)resolve;
                                }
                            } else if (qualifierExpression != null) {
                                psiClass = PsiUtil.resolveClassInType(qualifierExpression.getType());
                            }

                        }
                    } else if (superCall instanceof PsiNewExpression) {
                        final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)superCall).getClassOrAnonymousClassReference();
                        if (reference != null) {
                            final PsiElement resolve = reference.resolve();

                        }
                    }
                    return false;
                }

                public boolean isClassAcceptable(Class hintClass) {
                    return true;
                }
            }));

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    /*   registrar.registerReferenceProvider(FXML_PATTERN, new JavaFxFileReferenceProvider(RythmFileTypeFactory.RYTHM_EXTENSION));
        registrar.registerReferenceProvider(STYLESHEET_PATTERN, new JavaFxFileReferenceProvider("css") {
            @Override
            protected String preprocessValue(String value) {
                if (value.endsWith(".bss")) {
                    return StringUtil.trimEnd(value, ".bss") + ".css";
                }
                return value;
            }
        });*/
    }
}

