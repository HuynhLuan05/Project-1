import { ProfileRequest } from '../models/ProfileRequest';
import apiClientService from '@/common/services/ApiClientService';
import { YasError } from '@/common/services/errors/YasError';

export async function getMyProfile() {
  const url = '/api/customer/storefront/customer/profile';
  const response = await apiClientService.get(url);
  if (response.status === 401 || response.status === 403 || response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new YasError(await response.json());
  }
  return response.json();
}

export async function updateCustomer(profile: ProfileRequest) {
  const url = '/api/customer/';
  const response = await apiClientService.put(url, JSON.stringify(profile));
  if (response.status === 204) return response;
  else return await response.json();
}
