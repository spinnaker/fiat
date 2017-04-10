/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;

import java.util.Collection;
import java.util.Set;

public interface ResourceProvider<R extends Resource.AccessControlled> {

  Set<R> getAll() throws ProviderException;

  Set<R> getAllRestricted(Set<Role> roles) throws ProviderException;

  Set<R> getAllUnrestricted() throws ProviderException;
}
