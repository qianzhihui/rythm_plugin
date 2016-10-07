package com.rythmplugin.imports;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by mpl on 07.10.2016.
 */
public abstract class RythmJavClassTagDescriptorBase implements XmlElementDescriptor, Validator<XmlTag> {
    private final String myName;

    public RythmJavClassTagDescriptorBase(String name) {
        myName = name;
    }

    public abstract PsiClass getPsiClass();

    @Override
    public String getQualifiedName() {
        final PsiClass psiClass = getPsiClass();
        return psiClass != null ? psiClass.getQualifiedName() : getName();
    }

    @Override
    public String getDefaultName() {
        return getName();
    }

    @Override
    public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
        if (context != null) {
            final PsiClass psiClass = getPsiClass();
            if (psiClass != null) {
                final List<XmlElementDescriptor> children = new ArrayList<XmlElementDescriptor>();
                collectWritableProperties(children,
                        (member) -> new RythmJavPropertyTagDescriptor(psiClass, PropertyUtil.getPropertyName(member), false));

                final RythmJavPropertyTagDescriptor defaultPropertyDescriptor = getDefaultPropertyDescriptor();
                if (defaultPropertyDescriptor != null) {
                    Collections.addAll(children, defaultPropertyDescriptor.getElementsDescriptors(context));
                }
                else {
                    for (String name : RythmJavConstants.FX_BUILT_IN_TAGS) {
                        children.add(new RythmJavBuiltInTagDescriptor(name, null));
                    }
                }

                collectStaticElementDescriptors(context, children);

                if (!children.isEmpty()) {
                    return children.toArray(new XmlElementDescriptor[children.size()]);
                }
            }
        }
        return XmlElementDescriptor.EMPTY_ARRAY;
    }

    private RythmJavPropertyTagDescriptor getDefaultPropertyDescriptor() {
        final PsiClass psiClass = getPsiClass();
        final PsiAnnotation defaultProperty = AnnotationUtil
                .findAnnotationInHierarchy(psiClass, Collections.singleton(RythmJavCommonNames.JAVAFX_BEANS_DEFAULT_PROPERTY));
        if (defaultProperty != null) {
            final PsiAnnotationMemberValue defaultPropertyAttributeValue = defaultProperty.findAttributeValue(RythmJavConstants.VALUE);
            if (defaultPropertyAttributeValue instanceof PsiLiteralExpression) {
                final Object value = ((PsiLiteralExpression)defaultPropertyAttributeValue).getValue();
                if (value instanceof String) {
                    return new RythmJavPropertyTagDescriptor(psiClass, (String)value, false);
                }
            }
        }
        return null;
    }

    static void collectStaticAttributesDescriptors(@Nullable XmlTag context, List<XmlAttributeDescriptor> simpleAttrs) {
        if (context == null) return;
        collectParentStaticProperties(context.getParentTag(), simpleAttrs,
                method -> new RythmJavSetterAttributeDescriptor(method, method.getContainingClass()));
    }

    protected static void collectStaticElementDescriptors(XmlTag context, List<XmlElementDescriptor> children) {
        collectParentStaticProperties(context, children, method -> {
            final PsiClass aClass = method.getContainingClass();
            return new RythmJavPropertyTagDescriptor(aClass, PropertyUtil.getPropertyName(method.getName()), true);
        });
    }

    private static <T> void collectParentStaticProperties(XmlTag context, List<T> children, Function<PsiMethod, T> factory) {
        XmlTag tag = context;
        while (tag != null) {
            final XmlElementDescriptor descr = tag.getDescriptor();
            if (descr instanceof RythmJavClassTagDescriptorBase) {
                final PsiElement element = descr.getDeclaration();
                if (element instanceof PsiClass) {
                    final List<PsiMethod> setters = CachedValuesManager.getCachedValue(element, () -> {
                        final List<PsiMethod> meths = new ArrayList<PsiMethod>();
                        for (PsiMethod method : ((PsiClass)element).getAllMethods()) {
                            if (method.hasModifierProperty(PsiModifier.STATIC) && method.getName().startsWith("set")) {
                                final PsiParameter[] parameters = method.getParameterList().getParameters();
                                if (parameters.length == 2 &&
                                        InheritanceUtil.isInheritor(parameters[0].getType(), RythmJavCommonNames.JAVAFX_SCENE_NODE)) {
                                    meths.add(method);
                                }
                            }
                        }
                        return CachedValueProvider.Result.create(meths, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
                    });
                    for (PsiMethod setter : setters) {
                        children.add(factory.fun(setter));
                    }
                }
            }
            tag = tag.getParentTag();
        }
    }

    @Nullable
    @Override
    public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
        final String name = childTag.getName();
        if (RythmJavConstants.FX_BUILT_IN_TAGS.contains(name)) {
            return new RythmJavBuiltInTagDescriptor(name, childTag);
        }
        if (RythmJavConstants.FX_ROOT.equals(name)) {
            return new RythmJavRootTagDescriptor(childTag);
        }
        final String shortName = StringUtil.getShortName(name);
        if (!name.equals(shortName)) { //static property
            final PsiMethod propertySetter = RythmJavPsiUtil.findStaticPropertySetter(name, childTag);
            if (propertySetter != null) {
                return new RythmJavPropertyTagDescriptor(propertySetter.getContainingClass(), shortName, true);
            }

            final Project project = childTag.getProject();
            if (JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project)) == null) {
                return null;
            }
        }

        final PsiClass psiClass = getPsiClass();
        if (psiClass != null) {
            final String parentTagName = contextTag.getName();
            if (!RythmJavConstants.FX_DEFINE.equals(parentTagName)) {
                if (RythmJavConstants.FX_ROOT.equals(parentTagName)) {
                    final Map<String, PsiMember> properties = RythmJavPsiUtil.collectWritableProperties(psiClass);
                    if (properties.get(name) != null) {
                        return new RythmJavPropertyTagDescriptor(psiClass, name, false);
                    }
                } else {
                    final RythmJavPropertyTagDescriptor defaultPropertyDescriptor = getDefaultPropertyDescriptor();
                    if (defaultPropertyDescriptor != null) {
                        final String defaultPropertyName = defaultPropertyDescriptor.getName();
                        if (StringUtil.equalsIgnoreCase(defaultPropertyName, name) && !StringUtil.equals(defaultPropertyName, name)) {
                            final XmlElementDescriptor childDescriptor = defaultPropertyDescriptor.getElementDescriptor(childTag, contextTag);
                            if (childDescriptor != null) {
                                return childDescriptor;
                            }
                        }
                    }
                    final Map<String, PsiMember> properties = RythmJavPsiUtil.collectWritableProperties(psiClass);
                    if (properties.get(name) != null) {
                        return new RythmJavPropertyTagDescriptor(psiClass, name, false);
                    }
                }
            }
        }
        if (name.length() != 0 && Character.isLowerCase(name.charAt(0))) {
            return new RythmJavPropertyTagDescriptor(psiClass, name, false);
        }
        return new RythmJavClassTagDescriptor(name, childTag);
    }

    @Override
    public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
        if (context != null) {
            final String name = context.getName();
            if (Comparing.equal(name, getName())) {
                final PsiClass psiClass = getPsiClass();
                if (psiClass != null) {
                    final List<XmlAttributeDescriptor> descriptors = new ArrayList<XmlAttributeDescriptor>();
                    collectInstanceProperties(descriptors);
                    collectStaticAttributesDescriptors(context, descriptors);
                    for (String builtInAttributeName : RythmJavConstants.FX_BUILT_IN_ATTRIBUTES) {
                        descriptors.add(RythmJavBuiltInAttributeDescriptor.create(builtInAttributeName, psiClass));
                    }
                    return descriptors.isEmpty() ? XmlAttributeDescriptor.EMPTY : descriptors.toArray(XmlAttributeDescriptor.EMPTY);
                }
            }
        }
        return XmlAttributeDescriptor.EMPTY;
    }

    protected void collectInstanceProperties(List<XmlAttributeDescriptor> simpleAttrs) {
        collectWritableProperties(simpleAttrs,
                (member) -> new RythmJavPropertyAttributeDescriptor(PropertyUtil.getPropertyName(member), getPsiClass()));
    }

    private <T> void collectWritableProperties(final List<T> children, final Function<PsiMember, T> factory) {
        final Map<String, PsiMember> fieldList = RythmJavPsiUtil.collectWritableProperties(getPsiClass());
        for (PsiMember field : fieldList.values()) {
            children.add(factory.fun(field));
        }
    }

    @Nullable
    @Override
    public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
        final PsiClass psiClass = getPsiClass();
        if (psiClass == null) return null;
        if (RythmJavConstants.FX_BUILT_IN_ATTRIBUTES.contains(attributeName)) {
            return RythmJavBuiltInAttributeDescriptor.create(attributeName, psiClass);
        }
        final PsiMethod propertySetter = RythmJavPsiUtil.findStaticPropertySetter(attributeName, context);
        if (propertySetter != null) {
            return new RythmJavStaticSetterAttributeDescriptor(propertySetter, attributeName);
        }
        final PsiMember psiMember = RythmJavPsiUtil.collectWritableProperties(psiClass).get(attributeName);
        if (psiMember != null) {
            return new RythmJavPropertyAttributeDescriptor(attributeName, psiClass);
        }
        return null;
    }

    @Nullable
    @Override
    public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
        return getAttributeDescriptor(attribute.getName(), attribute.getParent());
    }

    @Override
    public XmlNSDescriptor getNSDescriptor() {
        return null;
    }

    @Nullable
    @Override
    public XmlElementsGroup getTopGroup() {
        return null;
    }

    @Override
    public int getContentType() {
        return CONTENT_TYPE_UNKNOWN;
    }

    @Nullable
    @Override
    public String getDefaultValue() {
        return null;
    }

    @Override
    public PsiElement getDeclaration() {
        return getPsiClass();
    }

    @Override
    public String getName(PsiElement context) {
        return getName();
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public void init(PsiElement element) {
    }

    @Override
    public Object[] getDependences() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public void validate(@NotNull XmlTag context, @NotNull Validator.ValidationHost host) {
        final XmlTag parentTag = context.getParentTag();
        if (parentTag != null) {
            final XmlAttribute attribute = context.getAttribute(RythmJavConstants.FX_CONTROLLER);
            if (attribute != null) {
                host.addMessage(attribute.getNameElement(), "fx:controller can only be applied to root element", Validator.ValidationHost.ErrorType.ERROR); //todo add delete/move to upper tag fix
            }
        }
        final Pair<PsiClass, Boolean> tagValueClassInfo = RythmJavPsiUtil.getTagValueClass(context, getPsiClass());
        final PsiClass aClass = tagValueClassInfo.getFirst();
        RythmJavPsiUtil.isClassAcceptable(parentTag, aClass, (errorMessage, errorType) ->
                host.addMessage(context.getNavigationElement(), errorMessage, errorType));
        boolean needInstantiate = !tagValueClassInfo.getSecond();
        if (needInstantiate && aClass != null && aClass.isValid()) {
            RythmJavPsiUtil.isAbleToInstantiate(aClass, errorMessage ->
                    host.addMessage(context, errorMessage, Validator.ValidationHost.ErrorType.ERROR));
        }
    }

    public boolean isReadOnlyAttribute(String attributeName) {
        final PsiClass psiClass = getPsiClass();
        return psiClass != null && !RythmJavPsiUtil.collectWritableProperties(psiClass).containsKey(attributeName);
    }

    @NotNull
    public static XmlElementDescriptor createTagDescriptor(XmlTag xmlTag) {
        final String name = xmlTag.getName();
        if (RythmJavConstants.FX_BUILT_IN_TAGS.contains(name)) {
            return new RythmJavBuiltInTagDescriptor(name, xmlTag);
        }
        if (RythmJavConstants.FX_ROOT.equals(name)) {
            return new RythmJavRootTagDescriptor(xmlTag);
        }
        return new RythmJavClassTagDescriptor(name, xmlTag);
    }
}

