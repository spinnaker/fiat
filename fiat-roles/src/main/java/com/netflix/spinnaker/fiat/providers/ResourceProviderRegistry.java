/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.api.Resource;
import com.netflix.spinnaker.fiat.api.ResourceLoader;
import com.netflix.spinnaker.fiat.config.ProviderCacheConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ResourceProviderRegistry {
  List<ResourceProvider<? extends Resource>> resourceProviders;
  List<ExtensionResourceProvider> extensionResourceProviders = new ArrayList<>();

  @Autowired
  public ResourceProviderRegistry(
      List<ResourceProvider<? extends Resource>> resourceProviders,
      Optional<List<ResourceLoader>> resourceLoaders,
      ProviderCacheConfig providerCacheConfig) {
    this.resourceProviders = resourceProviders;

    resourceLoaders.ifPresent(
        loaders ->
            loaders.forEach(
                l -> {
                  val erp = new ExtensionResourceProvider(l);
                  erp.setProviderCacheConfig(providerCacheConfig);
                  extensionResourceProviders.add(erp);
                }));
  }

  @Scheduled(fixedRateString = "${fiat.cache.refresh-interval:PT15S}")
  private void reloadExtensionCaches() {
    extensionResourceProviders.forEach(ExtensionResourceProvider::reloadCache);
  }

  public List<ResourceProvider<? extends Resource>> getAll() {
    val all = new ArrayList<ResourceProvider<? extends Resource>>(resourceProviders);
    extensionResourceProviders.forEach(all::add);
    return all;
  }

  static class ExtensionResourceProvider extends BaseResourceProvider {
    ResourceLoader loader;

    ExtensionResourceProvider(ResourceLoader loader) {
      this.loader = loader;
    }

    @Override
    protected Set<? extends Resource> loadAll() throws ProviderException {
      return loader.loadAll();
    }
  }
}
