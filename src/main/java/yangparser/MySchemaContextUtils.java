package yangparser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.opendaylight.yangtools.yang.common.AbstractQName;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.UnqualifiedQName;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.xpath.api.YangLocationPath;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathAxis;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class MySchemaContextUtils {
    private static final Splitter COLON_SPLITTER = Splitter.on(':');
    private static final Splitter SLASH_SPLITTER = Splitter.on('/').omitEmptyStrings();
    private static final Pattern GROUPS_PATTERN = Pattern.compile("\\[(.*?)\\]");
    private static final Pattern STRIP_PATTERN = Pattern.compile("\\[[^\\[\\]]*\\]");

    private MySchemaContextUtils() {
    }

    public static String getXpathFromSchemaNode(final SchemaContext schemaContext, final DataSchemaNode schemaNode) {
        if (schemaNode instanceof ChoiceSchemaNode) {
            return null;
        }
        String path = "";
        Module module = schemaContext.findModules(schemaNode.getQName().getNamespace()).iterator().next();
        if (schemaNode.isAugmenting()) {
            URI augmentingNS = schemaNode.getPath().getPathFromRoot().stream().findFirst().get().getNamespace();
            module = schemaContext.findModules(augmentingNS).iterator().next();
        }
        List<QName> pathName = new ArrayList<>();
        for (QName qName : schemaNode.getPath().getPathFromRoot()) {
            String lastPath = path;
            pathName.add(qName);
            if (!qName.getNamespace().equals(module.getQNameModule().getNamespace())) {
                Module otherModule = schemaContext.findModules(qName.getNamespace()).iterator().next();
                path += "/" + otherModule.getName() + ":" + qName.getLocalName();
            } else {
                path += "/" + module.getName() + ":" + qName.getLocalName();
            }

            try {
                Optional<DataSchemaNode> dataSchemaNodeOptional = module.findDataTreeChild(pathName);
                if (dataSchemaNodeOptional.isPresent()) {
                    DataSchemaNode dataSchemaNode = dataSchemaNodeOptional.get();
                    if (dataSchemaNode instanceof ListSchemaNode) {
                        ListSchemaNode listSchemaNode = (ListSchemaNode) dataSchemaNode;
                        if (listSchemaNode.getKeyDefinition().size() > 0) {
                            path += "[" + listSchemaNode.getKeyDefinition().get(0).getLocalName() + "]";
                        }
                    }
                } else {
                    throw new Exception();
                }
            } catch (Exception ex) {
                path = lastPath;
                pathName.remove(pathName.size() - 1);
            }
        }
        return path;
    }

    @Nullable
    public static SchemaNode resolveRelativeXPath(final SchemaContext context, final Module module, final String pathStr, final SchemaNode actualSchemaNode) {
        Preconditions.checkState(actualSchemaNode.getPath() != null, "Schema Path reference for Leafref cannot be NULL");
        return pathStr.startsWith("deref(") ? resolveDerefPath(context, module, actualSchemaNode, pathStr) : findTargetNode(context, resolveRelativePath(context, module, actualSchemaNode, doSplitXPath(pathStr)));
    }

    public static TypeDefinition<?> getBaseTypeForLeafRef(final LeafrefTypeDefinition typeDefinition, final SchemaContext schemaContext, final SchemaNode schema) {
        PathExpression pathStatement = typeDefinition.getPathStatement();
        String pathStr = stripConditionsFromXPathString(pathStatement);
        DataSchemaNode dataSchemaNode;
        if (pathStatement.isAbsolute()) {
            SchemaNode baseSchema;
            Optional basePotential;
            for (baseSchema = schema; baseSchema instanceof DerivableSchemaNode; baseSchema = (SchemaNode) basePotential.get()) {
                basePotential = ((DerivableSchemaNode) baseSchema).getOriginal();
                if (!basePotential.isPresent()) {
                    break;
                }
            }

            Module parentModule = findParentModuleOfReferencingType(schemaContext, baseSchema);
            dataSchemaNode = (DataSchemaNode) findTargetNode(schemaContext, xpathToQNamePath(schemaContext, parentModule, pathStr));
        } else {
            Module parentModule = findParentModule(schemaContext, schema);
            dataSchemaNode = (DataSchemaNode) resolveRelativeXPath(schemaContext, parentModule, pathStr, schema);
        }

        if (dataSchemaNode == null) {
            return null;
        } else {
            TypeDefinition<?> targetTypeDefinition = typeDefinition(dataSchemaNode);
            return targetTypeDefinition instanceof LeafrefTypeDefinition ? getBaseTypeForLeafRef((LeafrefTypeDefinition) targetTypeDefinition, schemaContext, (SchemaNode) dataSchemaNode) : targetTypeDefinition;
        }
    }

    public static DataSchemaNode getSchemaNodeForLeafRef(final LeafrefTypeDefinition typeDefinition, final SchemaContext schemaContext, final SchemaNode schema) {
        PathExpression pathStatement = typeDefinition.getPathStatement();
        String pathStr = stripConditionsFromXPathString(pathStatement);
        DataSchemaNode dataSchemaNode;
        if (pathStatement.isAbsolute()) {
            SchemaNode baseSchema;
            Optional basePotential;
            for (baseSchema = schema; baseSchema instanceof DerivableSchemaNode; baseSchema = (SchemaNode) basePotential.get()) {
                basePotential = ((DerivableSchemaNode) baseSchema).getOriginal();
                if (!basePotential.isPresent()) {
                    break;
                }
            }

            Module parentModule = findParentModuleOfReferencingType(schemaContext, baseSchema);
            dataSchemaNode = (DataSchemaNode) findTargetNode(schemaContext, xpathToQNamePath(schemaContext, parentModule, pathStr));
        } else {
            Module parentModule = findParentModule(schemaContext, schema);
            dataSchemaNode = (DataSchemaNode) resolveRelativeXPath(schemaContext, parentModule, pathStr, schema);
        }
        return dataSchemaNode;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public static SchemaNode findDataSchemaNode(final SchemaContext context, final Module module, final PathExpression nonCondXPath) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(module, "module");
        String strXPath = nonCondXPath.getOriginalString();
        Preconditions.checkArgument(strXPath.indexOf(91) == -1, "Revision Aware XPath may not contain a condition");
        return nonCondXPath.isAbsolute() ? findTargetNode(context, xpathToQNamePath(context, module, strXPath)) : null;
    }

    public static SchemaNode findDataSchemaNodeForRelativeXPath(final SchemaContext context, final Module module, final SchemaNode actualSchemaNode, final PathExpression relativeXPath) {
        Preconditions.checkState(!relativeXPath.isAbsolute(), "Revision Aware XPath MUST be relative i.e. MUST contains ../, for non relative Revision Aware XPath use findDataSchemaNode method");
        return resolveRelativeXPath(context, module, removePredicatesFromXpath(relativeXPath.getOriginalString()), actualSchemaNode);
    }

    public static Module findParentModule(final SchemaContext context, final SchemaNode schemaNode) {
        QName qname = schemaNode.getPath().getLastComponent();
        Preconditions.checkState(qname != null, "Schema Path contains invalid state of path parts. The Schema Path MUST contain at least ONE QName  which defines namespace and Local name of path.");
        return (Module) context.findModule(qname.getModule()).orElse(null);
    }

    public static SchemaNode findNodeInSchemaContext(final SchemaContext context, final Iterable<QName> path) {
        QName current = (QName) path.iterator().next();
        log.trace("Looking up module {} in context {}", current, path);
        Optional<Module> module = context.findModule(current.getModule());
        if (module.isEmpty()) {
            log.debug("Module {} not found", current);
            return null;
        } else {
            return findNodeInModule((Module) module.get(), path);
        }
    }

    private static String removePredicatesFromXpath(final String xpath) {
        return GROUPS_PATTERN.matcher(xpath).replaceAll("");
    }

    private static SchemaNode findNodeInModule(final Module module, final Iterable<QName> path) {
        if (!path.iterator().hasNext()) {
            log.debug("No node matching {} found in node {}", path, module);
            return null;
        } else {
            QName current = (QName) path.iterator().next();
            log.trace("Looking for node {} in module {}", current, module);
            SchemaNode foundNode = null;
            Iterable<QName> nextPath = nextLevel(path);
            foundNode = module.dataChildByName(current);
            if (foundNode != null && nextPath.iterator().hasNext()) {
                foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
            }

            if (foundNode == null) {
                foundNode = getGroupingByName((DataNodeContainer) module, current);
                if (foundNode != null && nextPath.iterator().hasNext()) {
                    foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                }
            }

            if (foundNode == null) {
                foundNode = getRpcByName(module, current);
                if (foundNode != null && nextPath.iterator().hasNext()) {
                    foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                }
            }

            if (foundNode == null) {
                foundNode = getNotificationByName(module, current);
                if (foundNode != null && nextPath.iterator().hasNext()) {
                    foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                }
            }

            if (foundNode == null) {
                log.debug("No node matching {} found in node {}", path, module);
            }

            return (SchemaNode) foundNode;
        }
    }

    private static SchemaNode findNodeIn(final SchemaNode parent, final Iterable<QName> path) {
        if (!path.iterator().hasNext()) {
            log.debug("No node matching {} found in node {}", path, parent);
            return null;
        } else {
            QName current = (QName) path.iterator().next();
            log.trace("Looking for node {} in node {}", current, parent);
            SchemaNode foundNode = null;
            Iterable<QName> nextPath = nextLevel(path);
            if (parent instanceof DataNodeContainer) {
                DataNodeContainer parentDataNodeContainer = (DataNodeContainer) parent;
                foundNode = parentDataNodeContainer.dataChildByName(current);
                if (foundNode != null && nextPath.iterator().hasNext()) {
                    foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                }

                if (foundNode == null) {
                    foundNode = getGroupingByName(parentDataNodeContainer, current);
                    if (foundNode != null && nextPath.iterator().hasNext()) {
                        foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                    }
                }
            }

            if (foundNode == null && parent instanceof ActionNodeContainer) {
                Optional<? extends SchemaNode> next = ((ActionNodeContainer) parent).getActions().stream().filter((act) -> {
                    return current.equals(act.getQName());
                }).findFirst();
                if (next.isPresent() && nextPath.iterator().hasNext()) {
                    foundNode = findNodeIn((SchemaNode) next.orElseThrow(), nextPath);
                }
            }

            if (foundNode == null && parent instanceof NotificationNodeContainer) {
                foundNode = (SchemaNode) ((NotificationNodeContainer) parent).getNotifications().stream().filter((notif) -> {
                    return current.equals(notif.getQName());
                }).findFirst().orElse(null);
                if (foundNode != null && nextPath.iterator().hasNext()) {
                    foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                }
            }

            if (foundNode == null && parent instanceof OperationDefinition) {
                OperationDefinition parentRpcDefinition = (OperationDefinition) parent;
                if (current.getLocalName().equals("input")) {
                    foundNode = parentRpcDefinition.getInput();
                    if (foundNode != null && nextPath.iterator().hasNext()) {
                        foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                    }
                }

                if (current.getLocalName().equals("output")) {
                    foundNode = parentRpcDefinition.getOutput();
                    if (foundNode != null && nextPath.iterator().hasNext()) {
                        foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                    }
                }

                if (foundNode == null) {
                    foundNode = getGroupingByName(parentRpcDefinition, current);
                    if (foundNode != null && nextPath.iterator().hasNext()) {
                        foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                    }
                }
            }

            if (foundNode == null && parent instanceof ChoiceSchemaNode) {
                foundNode = (SchemaNode) ((ChoiceSchemaNode) parent).findCase(current).orElse(null);
                if (foundNode != null && nextPath.iterator().hasNext()) {
                    foundNode = findNodeIn((SchemaNode) foundNode, nextPath);
                }

                if (foundNode == null) {
                    Iterator var10 = ((ChoiceSchemaNode) parent).getCases().iterator();

                    while (var10.hasNext()) {
                        CaseSchemaNode caseNode = (CaseSchemaNode) var10.next();
                        DataSchemaNode maybeChild = caseNode.dataChildByName(current);
                        if (maybeChild != null) {
                            foundNode = findNodeIn(maybeChild, nextPath);
                            break;
                        }
                    }
                }
            }

            if (foundNode == null) {
                log.debug("No node matching {} found in node {}", path, parent);
            }

            return (SchemaNode) foundNode;
        }
    }

    private static Iterable<QName> nextLevel(final Iterable<QName> path) {
        return Iterables.skip(path, 1);
    }

    private static RpcDefinition getRpcByName(final Module module, final QName name) {
        Iterator var2 = module.getRpcs().iterator();

        RpcDefinition rpc;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            rpc = (RpcDefinition) var2.next();
        } while (!rpc.getQName().equals(name));

        return rpc;
    }

    private static NotificationDefinition getNotificationByName(final Module module, final QName name) {
        Iterator var2 = module.getNotifications().iterator();

        NotificationDefinition notification;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            notification = (NotificationDefinition) var2.next();
        } while (!notification.getQName().equals(name));

        return notification;
    }

    private static GroupingDefinition getGroupingByName(final DataNodeContainer dataNodeContainer, final QName name) {
        Iterator var2 = dataNodeContainer.getGroupings().iterator();

        GroupingDefinition grouping;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            grouping = (GroupingDefinition) var2.next();
        } while (!grouping.getQName().equals(name));

        return grouping;
    }

    private static GroupingDefinition getGroupingByName(final OperationDefinition rpc, final QName name) {
        Iterator var2 = rpc.getGroupings().iterator();

        GroupingDefinition grouping;
        do {
            if (!var2.hasNext()) {
                return null;
            }

            grouping = (GroupingDefinition) var2.next();
        } while (!grouping.getQName().equals(name));

        return grouping;
    }

    private static List<QName> xpathToQNamePath(final SchemaContext context, final Module parentModule, final String xpath) {
        List<QName> path = new ArrayList();
        Iterator var4 = SLASH_SPLITTER.split(xpath).iterator();

        while (var4.hasNext()) {
            String pathComponent = (String) var4.next();
            path.add(stringPathPartToQName(context, parentModule, pathComponent));
        }

        return path;
    }

    private static QName stringPathPartToQName(final SchemaContext context, final Module parentModule, final String prefixedPathPart) {
        Objects.requireNonNull(context, "context");
        if (prefixedPathPart.indexOf(58) != -1) {
            Iterator<String> prefixedName = COLON_SPLITTER.split(prefixedPathPart).iterator();
            String modulePrefix = (String) prefixedName.next();
            Module module = resolveModuleForPrefix(context, parentModule, modulePrefix);
            Preconditions.checkArgument(module != null, "Failed to resolve xpath: no module found for prefix %s in module %s", modulePrefix, parentModule.getName());
            return QName.create(module.getQNameModule(), (String) prefixedName.next());
        } else {
            return QName.create(parentModule.getQNameModule(), prefixedPathPart);
        }
    }

    private static Module resolveModuleForPrefix(final SchemaContext context, final Module module, final String prefix) {
        Objects.requireNonNull(context, "context");
        if (prefix.equals(module.getPrefix())) {
            return module;
        } else {
            Iterator var3 = module.getImports().iterator();

            ModuleImport mi;
            do {
                if (!var3.hasNext()) {
                    return null;
                }

                mi = (ModuleImport) var3.next();
            } while (!prefix.equals(mi.getPrefix()));

            return (Module) context.findModule(mi.getModuleName(), mi.getRevision()).orElse(null);
        }
    }

    private static Iterable<QName> resolveRelativePath(final SchemaContext context, final Module module, final SchemaNode actualSchemaNode, final List<String> steps) {
        int colCount = normalizeXPath(steps);
        List<String> xpaths = colCount == 0 ? steps : steps.subList(colCount, steps.size());
        List<QName> walkablePath = createWalkablePath(actualSchemaNode.getPath().getPathFromRoot(), context, colCount);
        return walkablePath.size() - colCount >= 0 ? Iterables.concat(Iterables.limit(walkablePath, walkablePath.size() - colCount), Iterables.transform(xpaths, (input) -> {
            return stringPathPartToQName(context, module, input);
        })) : Iterables.concat(walkablePath, Iterables.transform(xpaths, (input) -> {
            return stringPathPartToQName(context, module, input);
        }));
    }

    private static List<QName> createWalkablePath(final Iterable<QName> schemaNodePath, final SchemaContext context, final int colCount) {
        List<Integer> indexToRemove = new ArrayList();
        List<QName> schemaNodePathRet = Lists.newArrayList(schemaNodePath);
        int j = 0;

        int i;
        for (i = schemaNodePathRet.size() - 1; i >= 0 && j != colCount; ++j) {
            SchemaNode nodeIn = findTargetNode(context, schemaNodePathRet);
            if (nodeIn instanceof CaseSchemaNode || nodeIn instanceof ChoiceSchemaNode) {
                indexToRemove.add(i);
                --j;
            }

            schemaNodePathRet.remove(i);
            --i;
        }

        schemaNodePathRet = Lists.newArrayList(schemaNodePath);
        Iterator var8 = indexToRemove.iterator();

        while (var8.hasNext()) {
            i = (Integer) var8.next();
            schemaNodePathRet.remove(i);
        }

        return schemaNodePathRet;
    }

    private static SchemaNode resolveDerefPath(final SchemaContext context, final Module module, final SchemaNode actualSchemaNode, final String xpath) {
        int paren = xpath.indexOf(41, 6);
        Preconditions.checkArgument(paren != -1, "Cannot find matching parentheses in %s", xpath);
        String derefArg = xpath.substring(6, paren).strip();
        SchemaNode derefTarget = findTargetNode(context, resolveRelativePath(context, module, actualSchemaNode, doSplitXPath(derefArg)));
        Preconditions.checkArgument(derefTarget != null, "Cannot find deref(%s) target node %s in context of %s", derefArg, actualSchemaNode);
        Preconditions.checkArgument(derefTarget instanceof TypedDataSchemaNode, "deref(%s) resolved to non-typed %s", derefArg, derefTarget);
        TypeDefinition<?> targetType = ((TypedDataSchemaNode) derefTarget).getType();
        if (targetType instanceof InstanceIdentifierTypeDefinition) {
            throw new UnsupportedOperationException("Cannot infer instance-identifier reference " + targetType);
        } else {
            Preconditions.checkArgument(targetType instanceof LeafrefTypeDefinition, "Illegal target type %s", targetType);
            PathExpression targetPath = ((LeafrefTypeDefinition) targetType).getPathStatement();
            log.debug("Derefencing path {}", targetPath);
            SchemaNode deref = targetPath.isAbsolute() ? findTargetNode(context, actualSchemaNode.getQName().getModule(), ((PathExpression.LocationPathSteps) targetPath.getSteps()).getLocationPath()) : findDataSchemaNodeForRelativeXPath(context, module, actualSchemaNode, targetPath);
            if (deref == null) {
                log.debug("Path {} could not be derefenced", targetPath);
                return null;
            } else {
                Preconditions.checkArgument(deref instanceof LeafSchemaNode, "Unexpected %s reference in %s", deref, targetPath);
                List<String> qnames = doSplitXPath(xpath.substring(paren + 1).stripLeading());
                return findTargetNode(context, resolveRelativePath(context, module, deref, qnames));
            }
        }
    }

    @Nullable
    private static SchemaNode findTargetNode(final SchemaContext context, final QNameModule localNamespace, final YangLocationPath path) {
        Deque<QName> ret = new ArrayDeque();
        UnmodifiableIterator var4 = path.getSteps().iterator();

        while (var4.hasNext()) {
            YangLocationPath.Step step = (YangLocationPath.Step) var4.next();
            if (step instanceof YangLocationPath.AxisStep) {
                YangXPathAxis axis = ((YangLocationPath.AxisStep) step).getAxis();
                Preconditions.checkState(axis == YangXPathAxis.PARENT, "Unexpected axis %s", axis);
                ret.removeLast();
            } else {
                Preconditions.checkState(step instanceof YangLocationPath.QNameStep, "Unhandled step %s in %s", step, path);
                ret.addLast(resolve(((YangLocationPath.QNameStep) step).getQName(), localNamespace));
            }
        }

        return findTargetNode(context, ret);
    }

    @Nullable
    private static SchemaNode findTargetNode(final SchemaContext context, final Iterable<QName> qnamePath) {
        Optional<DataSchemaNode> pureData = context.findDataTreeChild(qnamePath);
        return pureData.isPresent() ? (SchemaNode) pureData.get() : findNodeInSchemaContext(context, qnamePath);
    }

    private static QName resolve(final AbstractQName toResolve, final QNameModule localNamespace) {
        if (toResolve instanceof QName) {
            return (QName) toResolve;
        } else if (toResolve instanceof UnqualifiedQName) {
            return ((UnqualifiedQName) toResolve).bindTo(localNamespace);
        } else {
            throw new IllegalStateException("Unhandled step " + toResolve);
        }
    }

    @VisibleForTesting
    static int normalizeXPath(final List<String> xpath) {
        log.trace("Normalize {}", xpath);

        label24:
        while (true) {
            int leadingParents;
            for (leadingParents = 0; leadingParents != xpath.size(); ++leadingParents) {
                if (!"..".equals(xpath.get(leadingParents))) {
                    int dots = findDots(xpath, leadingParents + 1);
                    if (dots == -1) {
                        return leadingParents;
                    }

                    xpath.remove(dots - 1);
                    xpath.remove(dots - 1);
                    log.trace("Next iteration {}", xpath);
                    continue label24;
                }
            }

            return leadingParents;
        }
    }

    private static int findDots(final List<String> xpath, final int startIndex) {
        for (int i = startIndex; i < xpath.size(); ++i) {
            if ("..".equals(xpath.get(i))) {
                return i;
            }
        }

        return -1;
    }

    private static List<String> doSplitXPath(final String xpath) {
        List<String> ret = new ArrayList();
        Iterator var2 = SLASH_SPLITTER.split(xpath).iterator();

        while (var2.hasNext()) {
            String str = (String) var2.next();
            ret.add(str);
        }

        return ret;
    }


    private static Module findParentModuleOfReferencingType(final SchemaContext schemaContext, final SchemaNode schemaNode) {
        Preconditions.checkArgument(schemaContext != null, "Schema Context reference cannot be NULL!");
        Preconditions.checkArgument(schemaNode instanceof TypedDataSchemaNode, "Unsupported node %s", schemaNode);
        TypeDefinition<?> nodeType = ((TypedDataSchemaNode) schemaNode).getType();
        if (nodeType.getBaseType() == null) {
            return findParentModule(schemaContext, schemaNode);
        } else {
            while (nodeType.getBaseType() != null) {
                nodeType = nodeType.getBaseType();
            }

            return (Module) schemaContext.findModule(nodeType.getQName().getModule()).orElse(null);
        }
    }

    @VisibleForTesting
    static String stripConditionsFromXPathString(final PathExpression pathStatement) {
        return STRIP_PATTERN.matcher(pathStatement.getOriginalString()).replaceAll("");
    }

    private static TypeDefinition<?> typeDefinition(final DataSchemaNode node) {
        Preconditions.checkArgument(node instanceof TypedDataSchemaNode, "Unhandled parameter type %s", node);
        TypeDefinition<?> current = ((TypedDataSchemaNode) node).getType();

        for (TypeDefinition base = current.getBaseType(); base != null; base = base.getBaseType()) {
            current = base;
        }

        return current;
    }
}
