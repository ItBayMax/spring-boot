/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.configurationprocessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.lang.model.element.TypeElement;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.springframework.boot.configurationprocessor.metadata.ItemMetadata;
import org.springframework.boot.configurationprocessor.test.RoundEnvironmentTester;
import org.springframework.boot.configurationprocessor.test.TestableAnnotationProcessor;
import org.springframework.boot.configurationsample.lombok.LombokExplicitProperties;
import org.springframework.boot.configurationsample.lombok.LombokSimpleDataProperties;
import org.springframework.boot.configurationsample.lombok.LombokSimpleProperties;
import org.springframework.boot.configurationsample.simple.HierarchicalProperties;
import org.springframework.boot.configurationsample.simple.HierarchicalPropertiesGrandparent;
import org.springframework.boot.configurationsample.simple.HierarchicalPropertiesParent;
import org.springframework.boot.configurationsample.simple.SimpleProperties;
import org.springframework.boot.testsupport.compiler.TestCompiler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PropertyDescriptorResolver}.
 *
 * @author Stephane Nicoll
 */
public class PropertyDescriptorResolverTests {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void propertiesWithJavaBeanProperties() throws IOException {
		process(SimpleProperties.class, propertyNames((stream) -> assertThat(stream)
				.containsExactly("theName", "flag", "comparator")));
	}

	@Test
	public void propertiesWithJavaBeanHierarchicalProperties() throws IOException {
		process(HierarchicalProperties.class,
				Arrays.asList(HierarchicalPropertiesParent.class,
						HierarchicalPropertiesGrandparent.class),
				(type, metadataEnv) -> {
					PropertyDescriptorResolver resolver = new PropertyDescriptorResolver(
							metadataEnv);
					assertThat(
							resolver.resolve(type, null).map(PropertyDescriptor::getName))
									.containsExactly("third", "second", "first");
					assertThat(resolver.resolve(type, null)
							.map((descriptor) -> descriptor.resolveItemMetadata("test",
									metadataEnv))
							.map(ItemMetadata::getDefaultValue)).containsExactly("three",
									"two", "one");
				});
	}

	@Test
	public void propertiesWithLombokGetterSetterAtClassLevel() throws IOException {
		process(LombokSimpleProperties.class, propertyNames((stream) -> assertThat(stream)
				.containsExactly("name", "description", "counter", "number", "items")));
	}

	@Test
	public void propertiesWithLombokGetterSetterAtFieldLevel() throws IOException {
		process(LombokExplicitProperties.class,
				propertyNames((stream) -> assertThat(stream).containsExactly("name",
						"description", "counter", "number", "items")));
	}

	@Test
	public void propertiesWithLombokDataClass() throws IOException {
		process(LombokSimpleDataProperties.class,
				propertyNames((stream) -> assertThat(stream).containsExactly("name",
						"description", "counter", "number", "items")));
	}

	private BiConsumer<TypeElement, MetadataGenerationEnvironment> properties(
			Consumer<Stream<PropertyDescriptor<?>>> stream) {
		return (element, metadataEnv) -> {
			PropertyDescriptorResolver resolver = new PropertyDescriptorResolver(
					metadataEnv);
			stream.accept(resolver.resolve(element, null));
		};
	}

	private BiConsumer<TypeElement, MetadataGenerationEnvironment> propertyNames(
			Consumer<Stream<String>> stream) {
		return properties(
				(result) -> stream.accept(result.map(PropertyDescriptor::getName)));
	}

	private void process(Class<?> target,
			BiConsumer<TypeElement, MetadataGenerationEnvironment> consumer)
			throws IOException {
		process(target, Collections.emptyList(), consumer);
	}

	private void process(Class<?> target, Collection<Class<?>> additionalClasses,
			BiConsumer<TypeElement, MetadataGenerationEnvironment> consumer)
			throws IOException {
		BiConsumer<RoundEnvironmentTester, MetadataGenerationEnvironment> internalConsumer = (
				roundEnv, metadataEnv) -> {
			TypeElement element = roundEnv.getRootElement(target);
			consumer.accept(element, metadataEnv);
		};
		TestableAnnotationProcessor<MetadataGenerationEnvironment> processor = new TestableAnnotationProcessor<>(
				internalConsumer, new MetadataGenerationEnvironmentFactory());
		TestCompiler compiler = new TestCompiler(this.temporaryFolder);
		ArrayList<Class<?>> allClasses = new ArrayList<>();
		allClasses.add(target);
		allClasses.addAll(additionalClasses);
		compiler.getTask(allClasses.toArray(new Class<?>[0])).call(processor);
	}

}
