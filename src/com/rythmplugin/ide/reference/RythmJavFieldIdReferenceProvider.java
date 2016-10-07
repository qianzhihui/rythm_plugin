package com.rythmplugin.ide.reference;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.AttributeValueSelfReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.rythmplugin.ide.reference.provider.RythmJavControllerBasedReferenceProvider;
import com.rythmplugin.imports.RythmJavCommonNames;
import com.rythmplugin.imports.RythmJavConstants;
import com.rythmplugin.imports.RythmJavPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mpl on 07.10.2016.
 */
public class RythmJavFieldIdReferenceProvider extends RythmJavControllerBasedReferenceProvider {
    @Override
    protected PsiReference[] getReferencesByElement(@NotNull final PsiClass aClass,
                                                    final XmlAttributeValue xmlAttributeValue,
                                                    ProcessingContext context) {
        final String name = xmlAttributeValue.getValue();
        PsiMember fieldOrGetterMethod = aClass.findFieldByName(name, true);
        if (fieldOrGetterMethod == null) {
            final PsiMethod[] methods = aClass.findMethodsByName(name, true);
            for (PsiMethod method : methods) {
                if (method.getParameterList().getParameters().length == 0) {
                    fieldOrGetterMethod = method;
                    break;
                }
            }
        }
        return new PsiReference[]{
                new JavaFxControllerFieldRef(xmlAttributeValue, fieldOrGetterMethod, aClass),
                new AttributeValueSelfReference(xmlAttributeValue)};
    }

    public static class JavaFxControllerFieldRef extends PsiReferenceBase<XmlAttributeValue> {
        private final XmlAttributeValue myXmlAttributeValue;
        private final PsiMember myFieldOrMethod;
        private final PsiClass myAClass;

        public JavaFxControllerFieldRef(XmlAttributeValue xmlAttributeValue, PsiMember fieldOrMethod, PsiClass aClass) {
            super(xmlAttributeValue, true);
            myXmlAttributeValue = xmlAttributeValue;
            myFieldOrMethod = fieldOrMethod;
            myAClass = aClass;
        }

        public XmlAttributeValue getXmlAttributeValue() {
            return myXmlAttributeValue;
        }

        public PsiClass getAClass() {
            return myAClass;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            return myFieldOrMethod != null ? myFieldOrMethod : myXmlAttributeValue;
        }

        public boolean isUnresolved() {
            if (myFieldOrMethod == null && myAClass != null) {
                final XmlFile xmlFile = (XmlFile)myXmlAttributeValue.getContainingFile();
                if (xmlFile.getRootTag() != null && !RythmJavPsiUtil.isOutOfHierarchy(myXmlAttributeValue)) {
                    return true;
                }
            }
            return false;
        }

        @NotNull
        @Override
        public Object[] getVariants() {
            final PsiClass exactTagClass = RythmJavPsiUtil.getTagClass(myXmlAttributeValue);
            final PsiClass guessedTagClass = exactTagClass == null ? getGuessedTagClass() : null;

            final List<Object> fieldsToSuggest = new ArrayList<Object>();
            final PsiField[] fields = myAClass.getAllFields();
            for (PsiField psiField : fields) {
                if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
                    if (RythmJavPsiUtil.isVisibleInFxml(psiField)) {
                        final PsiType fieldType = psiField.getType();
                        final PsiClass fieldClass = (fieldType instanceof PsiClassType) ? ((PsiClassType)fieldType).resolve() : null;
                        if (fieldClass == null) {
                            fieldsToSuggest.add(psiField);
                        }
                        else if (exactTagClass != null) {
                            if (InheritanceUtil.isInheritorOrSelf(exactTagClass, fieldClass, true)) {
                                fieldsToSuggest.add(psiField);
                            }
                        }
                        else if (guessedTagClass == null || InheritanceUtil.isInheritorOrSelf(fieldClass, guessedTagClass, true)) {
                            fieldsToSuggest.add(psiField);
                        }
                    }
                }
            }
            return ArrayUtil.toObjectArray(fieldsToSuggest);
        }

        @Override
        public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
            final String newPropertyName = RythmJavPsiUtil.getPropertyName(newElementName, myFieldOrMethod instanceof PsiMethod);
            return super.handleElementRename(newPropertyName);
        }

        private PsiClass getGuessedTagClass() {
            final PsiElement xmlAttribute = myXmlAttributeValue.getParent();
            final XmlTag xmlTag = ((XmlAttribute)xmlAttribute).getParent();
            if (xmlTag == null) return null;
            final PsiElement parentTag = xmlTag.getParent();
            if (parentTag == null) return null;

            String className = null;
            if (parentTag instanceof XmlDocument) {
                className = RythmJavCommonNames.JAVAFX_SCENE_LAYOUT_PANE;
            }
            else if (parentTag.getParent() instanceof XmlDocument) {
                final String name = xmlTag.getName();
                if (!RythmJavConstants.FX_BUILT_IN_TAGS.contains(name)) {
                    className = RythmJavCommonNames.JAVAFX_SCENE_NODE;
                }
            }
            if (className == null) return null;

            return JavaPsiFacade.getInstance(myAClass.getProject()).findClass(className, GlobalSearchScope.allScope(myAClass.getProject()));
        }
    }
}
