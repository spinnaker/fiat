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
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class ResourceProviderRegistry {
  private final List<ResourceProvider<? extends Resource>> resourceProviders;
  private final List<ExtensionResourceProvider> extensionResourceProviders = new ArrayList<>();

  public ResourceProviderRegistry(
      List<ResourceProvider<? extends Resource>> resourceProviders,
      List<ResourceLoader> resourceLoaders,
      ProviderCacheConfig providerCacheConfig) {
    this.resourceProviders = resourceProviders;
    resourceLoaders.forEach(
        l -> {
          val erp = new ExtensionResourceProvider(l);
          erp.setProviderCacheConfig(providerCacheConfig);
          extensionResourceProviders.add(erp);
        });
  }

  @Scheduled(fixedRateString = "${fiat.cache.refresh-interval:PT15S}")
  private void reloadExtensionResourceCaches() {
    log.info("Reloading {} extension resource caches...", extensionResourceProviders.size());
    extensionResourceProviders.forEach(ExtensionResourceProvider::reloadCache);
  }

  public List<ResourceProvider<? extends Resource>> getAll() {
    val all = new ArrayList<ResourceProvider<? extends Resource>>(resourceProviders);
    extensionResourceProviders.forEach(all::add);
    return all;
  }

  @SuppressWarnings("rawtypes")
  static class ExtensionResourceProvider extends BaseResourceProvider {
    private final ResourceLoader loader;

    ExtensionResourceProvider(ResourceLoader loader) {
      this.loader = loader;
    }

    @Override
    protected Set<? extends Resource> loadAll() throws ProviderException {
      return loader.loadAll();
    }
  }
}
