/*
 * All content copyright (c) 2003-2006 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.object.config;

import com.tc.asm.ClassAdapter;
import com.tc.asm.ClassVisitor;
import com.tc.asm.ClassWriter;
import com.tc.aspectwerkz.reflect.ClassInfo;
import com.tc.aspectwerkz.reflect.MemberInfo;
import com.tc.config.schema.NewCommonL1Config;
import com.tc.object.Portability;
import com.tc.object.bytecode.ClassAdapterFactory;
import com.tc.object.bytecode.TransparencyClassAdapter;
import com.tc.object.config.schema.DSOInstrumentationLoggingOptions;
import com.tc.object.config.schema.DSORuntimeLoggingOptions;
import com.tc.object.config.schema.DSORuntimeOutputOptions;
import com.tc.object.config.schema.InstrumentedClass;
import com.tc.object.logging.InstrumentationLogger;
import com.terracottatech.config.Plugins;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Knows how to interpret the DSO client config and tell you things like whether a class is portable. This interface
 * extends DSOApplicationConfig which is a much simpler interface suitable for manipulating the config from the
 * perspective of generating a configuration file.
 */
public interface DSOClientConfigHelper extends DSOApplicationConfig {

  boolean shouldBeAdapted(ClassInfo classInfo);

  boolean isNeverAdaptable(String fullName);

  boolean isLogical(String theClass);

  DSOInstrumentationLoggingOptions getInstrumentationLoggingOptions();

  void verifyBootJarContents() throws IncompleteBootJarException, UnverifiedBootJarException;

  Iterator getAllSpecs();

  Iterator getAllUserDefinedBootSpecs();

  TransparencyClassAdapter createDsoClassAdapterFor(ClassVisitor writer, ClassInfo classInfo, InstrumentationLogger lgr,
                                                    ClassLoader caller, final boolean forcePortable);

  ClassAdapter createClassAdapterFor(ClassWriter writer, ClassInfo classInfo, InstrumentationLogger lgr, ClassLoader caller);

  ClassAdapter createClassAdapterFor(ClassWriter writer, ClassInfo classInfo, InstrumentationLogger lgr,
                                     ClassLoader caller, boolean disableSuperClassTypeChecking);

  boolean isCallConstructorOnLoad(String className);

  // String getChangeApplicatorClassNameFor(String className);
  Class getChangeApplicator(Class clazz);

  boolean isPortablePluginClass(Class clazz);

  void setPluginSpecs(PluginSpec[] pluginSpecs);

  TransparencyClassSpec getOrCreateSpec(String className);

  TransparencyClassSpec getOrCreateSpec(String className, String applicator);

  LockDefinition[] lockDefinitionsFor(int access, String className, String methodName, String description,
                                      String[] exceptions);

  boolean isRoot(String className, String fieldName);

  boolean isRootDSOFinal(String className, String fieldName, boolean isPrimitive);

  boolean isTransient(int modifiers, String classname, String field);

  boolean isVolatile(int modifiers, String classname, String field);

  String rootNameFor(String className, String fieldName);

  boolean isLockMethod(int access, String className, String methodName, String description, String[] exceptions);

  boolean isDistributedMethodCall(int modifiers, String className, String methodName, String description,
                                  String[] exceptions);

  TransparencyClassSpec getSpec(String className);

  boolean isDSOSessions(String name);

  DSORuntimeLoggingOptions runtimeLoggingOptions();

  DSORuntimeOutputOptions runtimeOutputOptions();

  DSOInstrumentationLoggingOptions instrumentationLoggingOptions();

  int getFaultCount();

  void addWriteAutolock(String methodPattern);

  void addSynchronousWriteAutolock(String methodPattern);

  void addLock(String methodPattern, LockDefinition lockDefinition);

  void addReadAutolock(String methodPattern);

  void addAutolock(String methodPattern, ConfigLockLevel type);

  void setFaultCount(int count);

  void addRoot(String className, String fieldName, String rootName, boolean addSpecForClass);

  void addRoot(String className, String fieldName, String rootName, boolean dsoFinal, boolean addSpecForClass);

  boolean matches(final Lock lock, final MemberInfo methodInfo);

  boolean matches(final String expression, final MemberInfo methodInfo);

  void addTransient(String className, String fieldName);

  void addTransientType(String className, String fieldName);

  String getOnLoadScriptIfDefined(String className);

  String getPostCreateMethodIfDefined(String className);

  String getOnLoadMethodIfDefined(String className);

  boolean isUseNonDefaultConstructor(Class clazz);

  void addIncludePattern(String expression);

  NewCommonL1Config getNewCommonL1Config();

  // Used for testing
  void addIncludePattern(String expression, boolean honorTransient);

  void addIncludePattern(String expression, boolean honorTransient, boolean oldStyleCallConstructorOnLoad,
                         boolean honorVolatile);

  // Used for testing and Spring
  void addIncludeAndLockIfRequired(String expression, boolean honorTransient, boolean oldStyleCallConstructorOnLoad,
                                   boolean honorVolatile, String lockExpression);

  // Used for testing
  void addExcludePattern(String expression);

  boolean hasIncludeExcludePatterns();

  boolean hasIncludeExcludePattern(String className);

  void addAspectModule(String pattern, String moduleName);

  Map getAspectModules();

  void addDSOSpringConfig(DSOSpringConfigHelper config);

  Collection getDSOSpringConfigs();

  void addDistributedMethodCall(String methodExpression);

  Portability getPortability();

  void removeSpec(String className);

  String getLogicalExtendingClassName(String className);

  void addUserDefinedBootSpec(String className, TransparencyClassSpec spec);

  void addApplicationName(String name);

  void addSynchronousWriteApplication(String name);

  void addInstrumentationDescriptor(InstrumentedClass classDesc);

  Plugins getPluginsForInitialization();

  void addNewPlugin(String name, String version);

  boolean hasCustomAdapter(String fullName);

  void addCustomAdapter(String name, ClassAdapterFactory adapterFactory);

  int getSessionLockType(String appName);

}
